/*******************************************************************************
 * Copyright (c) 2025 Fermi National Accelerator Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.phoebus.app.utility.acnet;

import gov.fnal.controls.auth.AuthInfo;
import gov.fnal.controls.auth.FermiAuthService;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Separator;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import org.phoebus.framework.spi.AppDescriptor;
import org.phoebus.framework.spi.AppInstance;
import org.phoebus.ui.javafx.ImageCache;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * AppDescriptor for Fermilab Keycloak login.
 * Opens a browser-based login dialog and displays user roles upon success.
 *
 * @author FNAL
 */
public class FermiLoginApp implements AppDescriptor
{
    private static final Logger logger = Logger.getLogger(FermiLoginApp.class.getName());

    public static final String DISPLAY_NAME = "Login";
    public static final String NAME = "fermiLogin";
    public static final Image icon = ImageCache.getImage(FermiLoginApp.class, "/icons/fermi-login-16.png");

    /** Singleton auth service shared across the application lifetime */
    private static FermiAuthService authService;

    @Override
    public String getDisplayName()
    {
        return DISPLAY_NAME;
    }

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public AppInstance create()
    {
        System.out.println("[FermiLogin] >>> Login button clicked <<<");
        logger.info("FermiLoginApp.create() called");

        if (authService != null && authService.isLoggedIn())
        {
            // Already logged in — show the role/user info GUI directly
            System.out.println("[FermiLogin] Already logged in as: " + authService.getPreferredUsername());
            logger.info("Already logged in, showing user info window");
            Platform.runLater(() -> showUserInfoWindow(authService));
        }
        else
        {
            System.out.println("[FermiLogin] Not logged in — starting authentication flow...");
            // Show a "logging in..." status window immediately so user knows something is happening
            Platform.runLater(() -> showProgressWindow());

            // Run login in background thread (opens browser)
            CompletableFuture.runAsync(() ->
            {
                try
                {
                    System.out.println("[FermiLogin] Initializing FermiAuthService...");
                    logger.info("Initializing FermiAuthService...");

                    // Quick connectivity check before attempting Keycloak init
                    System.out.println("[FermiLogin] Checking connectivity to ad-auth.fnal.gov...");
                    try
                    {
                        java.net.InetAddress addr = java.net.InetAddress.getByName("ad-auth.fnal.gov");
                        java.net.Socket socket = new java.net.Socket();
                        socket.connect(new java.net.InetSocketAddress(addr, 443), 5000);
                        socket.close();
                        System.out.println("[FermiLogin] Connectivity OK — server reachable.");
                    }
                    catch (Exception netEx)
                    {
                        System.err.println("[FermiLogin] NETWORK ERROR: Cannot reach ad-auth.fnal.gov:443 — " + netEx.getMessage());
                        throw new Exception("Cannot reach Fermilab auth server (ad-auth.fnal.gov:443). " +
                                "Are you on the Fermilab network or VPN?\nDetails: " + netEx.getMessage(), netEx);
                    }

                    System.out.println("[FermiLogin] Creating FermiAuthService instance...");
                    FermiAuthService svc = new FermiAuthService(new AuthInfo());
                    System.out.println("[FermiLogin] Calling svc.initialize() — fetching OIDC discovery doc...");
                    // Run initialize() with a hard timeout so it doesn't hang forever
                    java.util.concurrent.Future<?> initFuture = java.util.concurrent.Executors
                            .newSingleThreadExecutor()
                            .submit(() -> { try { svc.initialize(); } catch (Exception ex) { throw new RuntimeException(ex); } });
                    try
                    {
                        initFuture.get(10, java.util.concurrent.TimeUnit.SECONDS);
                    }
                    catch (java.util.concurrent.TimeoutException te)
                    {
                        initFuture.cancel(true);
                        throw new Exception("Timed out after 10s waiting for Keycloak discovery doc from ad-auth.fnal.gov. " +
                                "The server is reachable but not responding to OIDC requests.");
                    }
                    catch (java.util.concurrent.ExecutionException ee)
                    {
                        throw new Exception("initialize() failed: " + ee.getCause().getMessage(), ee.getCause());
                    }
                    System.out.println("[FermiLogin] initialize() complete. Calling login() — browser should open now...");
                    logger.info("Calling login() - browser should open now...");
                    svc.login();
                    System.out.println("[FermiLogin] Login successful! User: " + svc.getPreferredUsername()
                            + "  Email: " + svc.getEmail()
                            + "  Roles: " + svc.getRoles());
                    logger.info("Login successful: " + svc.getPreferredUsername());
                    authService = svc;
                    Platform.runLater(() ->
                    {
                        closeProgressWindow();
                        showUserInfoWindow(svc);
                    });
                }
                catch (Exception e)
                {
                    System.err.println("[FermiLogin] Login FAILED: " + e.getMessage());
                    e.printStackTrace();
                    logger.log(Level.SEVERE, "Login failed: " + e.getMessage(), e);
                    Platform.runLater(() ->
                    {
                        closeProgressWindow();
                        showErrorWindow(e.getMessage());
                    });
                }
            });
        }
        return null;
    }

    private static Stage progressStage;

    private static void showProgressWindow()
    {
        progressStage = new Stage();
        progressStage.setTitle("Fermilab Login");
        Label msg = new Label("Opening browser for authentication...\nPlease wait.");
        msg.setPadding(new Insets(20));
        msg.setFont(Font.font(13));
        progressStage.setScene(new Scene(new VBox(msg)));
        progressStage.setResizable(false);
        progressStage.show();
    }

    private static void closeProgressWindow()
    {
        if (progressStage != null)
        {
            progressStage.close();
            progressStage = null;
        }
    }

    /**
     * Shows a window with the authenticated user's info and roles.
     */
    private static void showUserInfoWindow(FermiAuthService svc)
    {
        Stage stage = new Stage();
        stage.setTitle("Fermilab Login - " + svc.getPreferredUsername());

        // --- Header ---
        Label titleLabel = new Label("Authenticated User");
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 16));

        Label nameLabel = new Label(
            svc.getGivenName() + " " + svc.getFamilyName());
        nameLabel.setFont(Font.font(13));

        Label usernameLabel = new Label("Username:  " + svc.getPreferredUsername());
        Label emailLabel    = new Label("Email:         " + svc.getEmail());

        // --- Roles list ---
        Label rolesTitle = new Label("Assigned Roles");
        rolesTitle.setFont(Font.font("System", FontWeight.BOLD, 13));
        rolesTitle.setPadding(new Insets(8, 0, 4, 0));

        List<String> roles = svc.getRoles();
        ListView<String> rolesList = new ListView<>();
        rolesList.getItems().addAll(roles);
        rolesList.setPrefHeight(Math.min(30 + roles.size() * 24, 200));
        VBox.setVgrow(rolesList, Priority.ALWAYS);

        // --- Logout button ---
        Button logoutBtn = new Button("Logout");
        logoutBtn.setStyle("-fx-base: #d9534f; -fx-text-fill: white;");
        logoutBtn.setOnAction(e ->
        {
            CompletableFuture.runAsync(() ->
            {
                try
                {
                    svc.logout();
                    authService = null;
                }
                catch (Exception ex)
                {
                    logger.log(Level.WARNING, "Logout error", ex);
                }
            });
            stage.close();
        });

        Button closeBtn = new Button("Close");
        closeBtn.setOnAction(e -> stage.close());

        HBox buttons = new HBox(8, logoutBtn, closeBtn);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        buttons.setPadding(new Insets(8, 0, 0, 0));

        // --- Layout ---
        VBox root = new VBox(6,
            titleLabel,
            nameLabel,
            new Separator(),
            usernameLabel,
            emailLabel,
            new Separator(),
            rolesTitle,
            rolesList,
            buttons
        );
        root.setPadding(new Insets(16));
        root.setPrefWidth(380);

        stage.setScene(new Scene(root));
        stage.setResizable(false);
        stage.show();
    }

    /**
     * Shows a small error window when login fails.
     */
    private static void showErrorWindow(String message)
    {
        Stage stage = new Stage();
        stage.setTitle("Login Failed");

        Label icon = new Label("✖");
        icon.setTextFill(Color.RED);
        icon.setFont(Font.font(20));

        Label msg = new Label(message != null ? message : "Unknown error during login.");
        msg.setWrapText(true);

        Button closeBtn = new Button("Close");
        closeBtn.setOnAction(e -> stage.close());

        HBox header = new HBox(8, icon, msg);
        header.setAlignment(Pos.CENTER_LEFT);

        VBox root = new VBox(12, header, closeBtn);
        root.setPadding(new Insets(16));
        root.setPrefWidth(360);
        VBox.setVgrow(msg, Priority.ALWAYS);

        stage.setScene(new Scene(root));
        stage.show();
    }

    /**
     * Returns the current auth service (may be null if not logged in).
     */
    public static FermiAuthService getAuthService()
    {
        return authService;
    }
}
