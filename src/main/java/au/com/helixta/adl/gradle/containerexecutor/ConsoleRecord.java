package au.com.helixta.adl.gradle.containerexecutor;

import com.github.dockerjava.api.model.StreamType;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOError;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A single log message record that came from Docker.
 */
public class ConsoleRecord
{
    private final StreamType type;
    private final ByteArrayOutputStream messageBuffer = new ByteArrayOutputStream();

    //Keeping everything as bytes for as long as possible in case a multi-byte character is split across boundaries
    public ConsoleRecord(StreamType type, byte[] messageBytes)
    {
        this.type = type;
        append(messageBytes);
    }

    /**
     * @return message type, stdout or stderr.
     */
    public StreamType getType()
    {
        return type;
    }

    /**
     * @return the entire log message.
     */
    public String getMessage()
    {
        try
        {
            return messageBuffer.toString(StandardCharsets.UTF_8.name());
        }
        catch (UnsupportedEncodingException e)
        {
            //UTF-8 should always be supported
            throw new IOError(e);
        }
    }

    /**
     * @return a list of log messages, separate strings for each line of log output.
     */
    public List<String> getMessageLines()
    {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(messageBuffer.toByteArray()), StandardCharsets.UTF_8)))
        {
            return br.lines().collect(Collectors.toList());
        }
        catch (IOException e)
        {
            //Shouldn't happen for in-memory - and UTF-8 encoding errors will decode to bad chars not throw exception
            throw new IOError(e);
        }
    }

    /**
     * Appends to the existing log message in this record.
     *
     * @param messageBytes raw message bytes from Docker.
     */
    public void append(byte[] messageBytes)
    {
        try
        {
            messageBuffer.write(messageBytes);
        }
        catch (IOException e)
        {
            //In-memory, should not happen
            throw new IOError(e);
        }
    }
}
