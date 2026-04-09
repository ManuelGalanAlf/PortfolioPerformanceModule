package name.abuchen.portfolio.ui.views.dashboard;

import java.util.UUID;

import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;

import name.abuchen.portfolio.model.Dashboard;
import name.abuchen.portfolio.ui.Messages;

public class PortfolioMetricsView extends DashboardView
{
    private static final String SELECTED_DASHBOARD_KEY = "selected-optimization-dashboard"; //$NON-NLS-1$

    @Override
    protected Control createBody(Composite parent)
    {
        initializeSelectedDashboardWithOptimizationWidgets();
        return super.createBody(parent);
    }

    @Override
    protected String getDefaultTitle()
    {
        return Messages.LabelOptimization;
    }

    @Override
    protected String getSelectedDashboardStateKey()
    {
        return SELECTED_DASHBOARD_KEY;
    }

    private void initializeSelectedDashboardWithOptimizationWidgets()
    {
        var dashboards = getClient().getDashboards().toList();
        int selectedIndex = Math.max(0, persistedState.getInt(SELECTED_DASHBOARD_KEY));
        Dashboard selectedDashboard = selectedIndex < dashboards.size() ? dashboards.get(selectedIndex) : null;
        boolean changed = false;

        if (!isPortfolioMetricsDashboard(selectedDashboard))
        {
            selectedDashboard = dashboards.stream().filter(this::isPortfolioMetricsDashboard).findFirst().orElse(null);

            if (selectedDashboard == null)
            {
                selectedDashboard = new Dashboard(UUID.randomUUID().toString());
                selectedDashboard.setName(Messages.LabelPortfolioMetrics);
                addOptimizationWidgets(selectedDashboard);
                getClient().addDashboard(selectedDashboard);
                dashboards = getClient().getDashboards().toList();
                changed = true;
            }

            int index = dashboards.indexOf(selectedDashboard);
            if (index >= 0 && index != selectedIndex)
                persistedState.setValue(SELECTED_DASHBOARD_KEY, index);
        }

        if (!hasOptimizationWidgets(selectedDashboard))
        {
            addOptimizationWidgets(selectedDashboard);
            changed = true;
        }

        if (changed)
            getClient().touch();
    }

    private boolean isPortfolioMetricsDashboard(Dashboard dashboard)
    {
        if (dashboard == null)
            return false;

        if (Messages.LabelPortfolioMetrics.equals(dashboard.getName()))
            return true;

        boolean hasWidgets = false;

        for (Dashboard.Column column : dashboard.getColumns())
        {
            for (Dashboard.Widget widget : column.getWidgets())
            {
                hasWidgets = true;

                try
                {
                    if (!WidgetFactory.valueOf(widget.getType()).isOptimizationWidget())
                        return false;
                }
                catch (IllegalArgumentException e)
                {
                    return false;
                }
            }
        }

        return hasWidgets;
    }

    private boolean hasOptimizationWidgets(Dashboard dashboard)
    {
        return dashboard.getColumns().stream().flatMap(column -> column.getWidgets().stream()).anyMatch(widget -> {
            try
            {
                return WidgetFactory.valueOf(widget.getType()).isOptimizationWidget();
            }
            catch (IllegalArgumentException e)
            {
                return false;
            }
        });
    }

    private void addOptimizationWidgets(Dashboard dashboard)
    {
        Dashboard.Column leftColumn = new Dashboard.Column();
        Dashboard.Column rightColumn = new Dashboard.Column();

        for (WidgetFactory type : WidgetFactory.values())
        {
            if (type.isOptimizationWidget())
            {
                if (type == WidgetFactory.PORTFOLIO_ANALYTICS_RISK_CONTRIBUTION_CHART)
                    rightColumn.getWidgets().add(type.constructWidget());
                else
                    leftColumn.getWidgets().add(type.constructWidget());
            }
        }

        dashboard.getColumns().add(0, leftColumn);

        if (!rightColumn.getWidgets().isEmpty())
            dashboard.getColumns().add(1, rightColumn);
    }

    @Override
    protected boolean isWidgetAccessibleInView(WidgetFactory type)
    {
        return type.isOptimizationWidget();
    }
}