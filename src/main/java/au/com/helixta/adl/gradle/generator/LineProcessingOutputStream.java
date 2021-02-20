package au.com.helixta.adl.gradle.generator;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LineProcessingOutputStream extends OutputStream
{
    private static final Pattern lineEndingPattern = Pattern.compile(".*(\r\n|[\n\r\u2028\u2029\u0085])");

    private final ByteBuffer buf;
    private final CharBuffer cbuf;
    private final CharsetDecoder decoder;

    private final Consumer<String> lineProcessor;

    public LineProcessingOutputStream(Charset charset, Consumer<String> lineProcessor)
    {
        this(4096, charset, lineProcessor);
    }

    public LineProcessingOutputStream(int bufferSize, Charset charset, Consumer<String> lineProcessor)
    {
        this.buf = ByteBuffer.allocate(bufferSize);
        this.cbuf = CharBuffer.allocate(bufferSize);
        this.decoder = charset.newDecoder()
                              .onMalformedInput(CodingErrorAction.REPLACE)
                              .onUnmappableCharacter(CodingErrorAction.REPLACE);
        this.lineProcessor = lineProcessor;
    }

    @Override
    public void write(int b) throws IOException
    {
        buf.put((byte)b);
        buf.flip();
        CoderResult result = decoder.decode(buf, cbuf, false);
        if (result.isOverflow())
        {
            //Clear out what we have
            processLines(true);
            result = decoder.decode(buf, cbuf, false);
        }
        if (result.isUnderflow())
        {
            // Underflow, prepare the buffer for more writing
            buf.compact();
        }

        processLines(false);
    }

    @Override
    public void close() throws IOException
    {
        buf.flip();

        CoderResult result = decoder.decode(buf, cbuf, true);
        if (result.isOverflow())
        {
            processLines(true);
            result = decoder.decode(buf, cbuf, true);
        }

        result = decoder.flush(cbuf);
        if (result.isOverflow())
        {
            processLines(true);
            result = decoder.flush(cbuf);
        }
        processLines(true);
    }

    private void processLines(boolean force)
    {
        cbuf.flip();

        //Line scanning
        Matcher m = lineEndingPattern.matcher(cbuf);
        while (m.find())
        {
            int lineEndPos = m.start(1);
            int lineEndLength = m.group(1).length();
            char[] lineData = new char[lineEndPos];
            cbuf.get(lineData);
            cbuf.position(lineEndPos + lineEndLength);

            if (lineData.length > 0)
                lineProcessor.accept(new String(lineData));
        }

        //If force, get any remaining data
        if (force)
        {
            String remainder = cbuf.toString();
            if (remainder.length() > 0)
                lineProcessor.accept(remainder);

            cbuf.clear();
        }
        else
            cbuf.compact();
    }
}
