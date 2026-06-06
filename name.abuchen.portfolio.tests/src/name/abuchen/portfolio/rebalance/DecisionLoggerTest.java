package name.abuchen.portfolio.rebalance;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.StringContains.containsString;

import java.util.List;

import org.junit.Test;

public class DecisionLoggerTest
{
    @Test
    public void testLogEntryCreation()
    {
        DecisionLogger logger = new DecisionLogger();
        logger.log("TestLayer", "Test message");

        List<DecisionLogger.LogEntry> entries = logger.getLogs();
        assertThat(entries.size(), is(1));

        DecisionLogger.LogEntry entry = entries.get(0);
        assertThat(entry.getLayerName(), is("TestLayer"));
        assertThat(entry.getMessage(), is("Test message"));
    }

    @Test
    public void testClearLogs()
    {
        DecisionLogger logger = new DecisionLogger();
        logger.log("TestLayer", "Message 1");
        logger.log("TestLayer", "Message 2");

        assertThat(logger.getLogs().size(), is(2));

        logger.clear();

        assertThat(logger.getLogs().size(), is(0));
    }

    @Test
    public void testToStringFormatting()
    {
        DecisionLogger logger = new DecisionLogger();
        logger.log("LayerA", "Something happened");

        DecisionLogger.LogEntry entry = logger.getLogs().get(0);
        String entryString = entry.toString();
        assertThat(entryString, containsString("[LayerA]"));
        assertThat(entryString, containsString("Something happened"));
    }
}
