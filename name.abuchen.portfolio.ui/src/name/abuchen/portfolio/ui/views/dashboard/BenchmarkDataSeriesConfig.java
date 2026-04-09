package name.abuchen.portfolio.ui.views.dashboard;

import name.abuchen.portfolio.model.Dashboard;
import name.abuchen.portfolio.ui.Messages;

public class BenchmarkDataSeriesConfig extends DataSeriesConfig
{
    public BenchmarkDataSeriesConfig(WidgetDelegate<?> delegate)
    {
        super(delegate, true, true, null, Messages.LabelExcessReturnBaselineDataSeries,
                        Dashboard.Config.SECONDARY_DATA_SERIES);
    }
}
