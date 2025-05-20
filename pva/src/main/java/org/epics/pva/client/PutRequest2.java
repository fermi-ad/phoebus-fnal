/*******************************************************************************
 * Copyright (c) 2019 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 ******************************************************************************/
package org.epics.pva.client;

import org.epics.pva.common.PVAHeader;
import org.epics.pva.common.RequestEncoder;
import org.epics.pva.data.*;

import java.nio.ByteBuffer;
import java.util.BitSet;
import java.util.logging.Level;

import static org.epics.pva.PVASettings.logger;

@SuppressWarnings("nls")
public class PutRequest2 implements RequestEncoder, ResponseHandler
{
    private final PVAChannel channel;

    private final String request;

    private final int request_id;

    private volatile PVAStructure data;

    private final Object new_value;

	private final MonitorListener listener;

	private final BitSet empty = new BitSet();

    /** INIT or PUT? */
    private volatile boolean init = true;

    /** Request to write channel's value
     *  @param channel {@link PVAChannel}
     *  @param request Request for element to write, e.g. "field(value)"
     *  @param new_value Value to write.
     *                   Must be accepted by {@link PVAData#setValue(Object)}
     *                   for the requested field.
     */
    public PutRequest2(final PVAChannel channel, final String request, final Object new_value, final MonitorListener listener) throws Exception
    {
        this.channel = channel;
        this.request = request;
        this.request_id = channel.getClient().allocateRequestID();
        this.new_value = new_value;
		this.listener = listener;
        channel.getTCP().submit(this, this);
    }

	PVAStructure status(int status)
	{
		return new PVAStructure("", "fermi:nt/NTAcnetStatus:1.0", new PVAInt("value", status));
	}

    @Override
    public int getRequestID()
    {
        return request_id;
    }

    @Override
    public void encodeRequest(final byte version, final ByteBuffer buffer) throws Exception
    {
        if (init)
        {
            logger.log(Level.FINE, () -> "Sending put INIT request #" + request_id + " for " + channel + " '" + request + "'");

            // Guess, assumes empty FieldRequest (6)
            final int size_offset = buffer.position() + PVAHeader.HEADER_OFFSET_PAYLOAD_SIZE;
            PVAHeader.encodeMessageHeader(buffer, PVAHeader.FLAG_NONE, PVAHeader.CMD_PUT, 4+4+1+6);
            final int payload_start = buffer.position();
            buffer.putInt(channel.getSID());
            buffer.putInt(request_id);
            buffer.put(PVAHeader.CMD_SUB_INIT);

            final FieldRequest field_request = new FieldRequest(request);
            field_request.encodeType(buffer);
            field_request.encode(buffer);
            buffer.putInt(size_offset, buffer.position() - payload_start);

            init = false;
        }
        else
        {

            logger.log(Level.FINE, () -> "Sending put PUT request #" + request_id + " for " + channel + " = " + new_value);

            // Guess, empty bitset (1)
            final int size_offset = buffer.position() + PVAHeader.HEADER_OFFSET_PAYLOAD_SIZE;
            PVAHeader.encodeMessageHeader(buffer, PVAHeader.FLAG_NONE, PVAHeader.CMD_PUT, 4+4+1+1);
            final int pos = buffer.position();
            buffer.putInt(channel.getSID());
            buffer.putInt(request_id);
            buffer.put(PVAHeader.CMD_SUB_DESTROY);

			PVAData field = null;
            try
            {
                field = data.locate(request);
            }
            catch (Exception ex)
            {
				try {
					field = data.locate("value");
				} catch (Exception e) {
					fail(ex);
				}
            }	

            if (field instanceof PVAStructure)
            {
                final PVAStructure struct = (PVAStructure) field;
                // For enumerated type, write to index.
                if ("enum_t".equals(struct.getStructureName()) ||
                    data.getStructureName().toLowerCase().indexOf("ntenum") > 0)
                    field = struct.get("index");
				else
					field = struct.get("value");
            }

			if (field == null) {
				fail(new Exception("Cannot locate 'value' to write in \n" + data));
				return;
			}

            // Bitset to describe which field we're about to write
            final BitSet changed = new BitSet();
            changed.set(data.getIndex(field));
            logger.log(Level.FINE, () -> "Updated structure elements: " + changed);
            PVABitSet.encodeBitSet(changed, buffer);

            // Write the updated field
            field.setValue(new_value);
            field.encode(buffer);

            // Fix message size
            final int request_size = buffer.position() - pos;
            buffer.putInt(size_offset, request_size);
        }
    }

    @Override
    public void handleResponse(final ByteBuffer buffer) throws Exception
    {
        if (buffer.remaining() < 4+1+1)
            status(-1);

        final int request_id = buffer.getInt();
        final byte subcmd = buffer.get();
        PVAStatus status = PVAStatus.decode(buffer);
        if (! status.isSuccess())
            fail(new Exception(channel + " Put Response for " + request + ": " + status));

        if (subcmd == PVAHeader.CMD_SUB_INIT)
        {
            logger.log(Level.FINE,
                       () -> "Received put INIT reply #" + request_id +
                             " for " + channel + ": " + status);

            // Decode type description from INIT response
            final PVAData type = channel.getTCP().getTypeRegistry().decodeType("", buffer);
            if (type instanceof PVAStructure)
            {
                data = (PVAStructure)type;
                logger.log(Level.FINER, () -> "Introspection Info: " + data.formatType());
            }
            else
            {
                data = null;
                fail(new Exception("Expected PVAStructure, got " + type));
            }

            // Submit request again, this time to PUT data
            channel.getTCP().submit(this, this);
        }
        else if (subcmd == PVAHeader.CMD_SUB_DESTROY)
        {
            logger.log(Level.FINE,
                    () -> "Received put PUT reply #" + request_id +
                          " for " + channel + ": " + status);
            // Indicate completion now that server confirmed PUT

			listener.handleMonitor(channel, empty, empty, status(0));
        }
        else
            fail(new Exception("Cannot decode Put " + subcmd + " Reply #" + request_id));
    }

    private void fail(final Exception ex) throws Exception
    {
		listener.handleMonitor(channel, empty, empty, status(-1));
		throw ex;
    }
}
