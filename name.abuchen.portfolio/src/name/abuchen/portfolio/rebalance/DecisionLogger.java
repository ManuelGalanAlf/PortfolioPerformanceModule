package name.abuchen.portfolio.rebalance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Logs the decisions made by the various layers of the RebalancingEngine.
 * This ensures the rebalancing process is auditable and explainable.
 */
public class DecisionLogger
{
    
    public static class LogEntry
    {
        private final String layerName;
        private final String message;

        public LogEntry(String layerName, String message)
        {
            this.layerName = layerName;
            this.message = message;
        }

        public String getLayerName()
        {
            return layerName;
        }

        public String getMessage()
        {
            return message;
        }
        
        @Override
        public String toString()
        {
            return String.format("[%s] %s", layerName, message);
        }
    }

    private final List<LogEntry> logs = new ArrayList<>();

    public void log(String layerName, String message)
    {
        logs.add(new LogEntry(layerName, message));
    }

    public List<LogEntry> getLogs()
    {
        return Collections.unmodifiableList(logs);
    }
    
    public void clear()
    {
        logs.clear();
    }
}
