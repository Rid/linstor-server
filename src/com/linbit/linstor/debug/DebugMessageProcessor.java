package com.linbit.linstor.debug;

import com.linbit.linstor.logging.ErrorReporter;
import com.linbit.linstor.netcom.IllegalMessageStateException;
import com.linbit.linstor.netcom.Message;
import com.linbit.linstor.netcom.MessageProcessor;
import com.linbit.linstor.netcom.Peer;
import com.linbit.linstor.netcom.TcpConnector;
import com.linbit.linstor.security.AccessContext;

/**
 * Debugger for messages
 *
 * @author Robert Altnoeder &lt;robert.altnoeder@linbit.com&gt;
 */
public class DebugMessageProcessor implements MessageProcessor
{
    private final ErrorReporter errorReporter;

    public DebugMessageProcessor(ErrorReporter errorReporterRef)
    {
        errorReporter = errorReporterRef;
    }

    @Override
    public void processMessage(Message msg, TcpConnector connector, Peer client)
    {
        try
        {
            System.out.println("MessageProcessor.processMessage() called");
            AccessContext accCtx = client.getAccessContext();
            System.out.printf(
                "Message from '%s' (Role '%s', Domain '%s') received from peer\n",
                accCtx.getIdentity(), accCtx.getRole(), accCtx.getDomain()
            );
            HexViewer.dumpByteArray(msg.getData());
        }
        catch (IllegalMessageStateException msgExc)
        {
            errorReporter.reportError(msgExc);
        }
    }
}
