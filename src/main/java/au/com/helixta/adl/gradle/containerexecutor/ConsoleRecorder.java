package au.com.helixta.adl.gradle.containerexecutor;

import com.github.dockerjava.api.model.StreamType;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads console output from Docker and records it in a set of records which can be played back to the host console later.
 */
public class ConsoleRecorder
{
    private final List<ConsoleRecord> records = new ArrayList<>();

    /**
     * Consumes log messages from a Docker container.
     *
     * @param type message type, stdout or stderr.
     * @param messageBytes bytes for the log message.
     */
    public synchronized void add(StreamType type, byte[] messageBytes)
    {
        //Append to existing last record if the same type
        if (!records.isEmpty())
        {
            ConsoleRecord lastRecord = records.get(records.size() - 1);
            if (lastRecord.getType() == type)
            {
                lastRecord.append(messageBytes);
                return;
            }
        }

        //Otherwise new record
        records.add(new ConsoleRecord(type, messageBytes));
    }

    /**
     * @return a list of all console log records that have been come from the ADL Docker container so far.
     */
    public synchronized List<? extends ConsoleRecord> getRecords()
    {
        return new ArrayList<>(records);
    }
}
