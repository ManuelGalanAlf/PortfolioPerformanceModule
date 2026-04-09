package name.abuchen.portfolio.ui.views.dashboard.charts;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swtchart.ICircularSeries;
import org.eclipse.swtchart.ISeries.SeriesType;
import org.eclipse.swtchart.model.Node;

import name.abuchen.portfolio.math.Covariance;
import name.abuchen.portfolio.math.PortfolioAnalytics;
import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.model.Dashboard.Widget;
import name.abuchen.portfolio.model.Portfolio;
import name.abuchen.portfolio.model.Security;
import name.abuchen.portfolio.snapshot.PerformanceIndex;
import name.abuchen.portfolio.ui.Messages;
import name.abuchen.portfolio.ui.UIConstants;
import name.abuchen.portfolio.ui.util.Colors;
import name.abuchen.portfolio.ui.util.chart.CircularChart;
import name.abuchen.portfolio.ui.util.chart.CircularChartToolTip;
import name.abuchen.portfolio.ui.views.dashboard.ClientFilterConfig;
import name.abuchen.portfolio.ui.views.dashboard.DashboardData;
import name.abuchen.portfolio.ui.views.dashboard.DataSeriesConfig;
import name.abuchen.portfolio.ui.views.dashboard.ReportingPeriodConfig;
import name.abuchen.portfolio.ui.views.dataseries.DataSeries;
import name.abuchen.portfolio.util.Interval;
import name.abuchen.portfolio.money.Values;

public class PortfolioRiskContributionChartWidget extends CircularChartWidget<List<PortfolioRiskContributionChartWidget.RiskContributionItem>>
{
    public record RiskContributionItem(String id, String label, double contribution)
    {
    }

    public PortfolioRiskContributionChartWidget(Widget widget, DashboardData data)
    {
        super(widget, data);

        addConfig(new DataSeriesConfig(this, false, PortfolioRiskContributionChartWidget::isSupportedDataSeries));
        addConfig(new ReportingPeriodConfig(this));
    }

    @Override
    protected void configureTooltip(CircularChartToolTip toolTip)
    {
        toolTip.setToolTipBuilder((composite, currentNode) -> {
            Composite data = new Composite(composite, SWT.NONE);
            GridLayoutFactory.swtDefaults().numColumns(2).applyTo(data);

            RiskContributionItem item = (RiskContributionItem) currentNode.getData();

            Label title = new Label(data, SWT.NONE);
            title.setData(UIConstants.CSS.CLASS_NAME, UIConstants.CSS.HEADING2);
            title.setText(item != null ? item.label() : Messages.LabelRiskContributionChart);

            if (item != null)
            {
                Label right = new Label(data, SWT.NONE);
                right.setText(Values.Percent2.format(item.contribution()));
            }
        });
    }

    @Override
    public Supplier<List<RiskContributionItem>> getUpdateTask()
    {
        DataSeries dataSeries = get(DataSeriesConfig.class).getDataSeries();
        Interval period = get(ReportingPeriodConfig.class).getReportingPeriod().toInterval(LocalDate.now());

        return () -> calculateRiskContributions(dataSeries, period);
    }

    private List<RiskContributionItem> calculateRiskContributions(DataSeries dataSeries, Interval period)
    {
        List<Security> candidates = collectCandidateSecurities(dataSeries);
        if (candidates.isEmpty())
            return List.of();

        List<PerformanceIndex> indices = new ArrayList<>();
        List<Security> securities = new ArrayList<>();
        List<Long> marketValues = new ArrayList<>();

        for (Security security : candidates)
        {
            PerformanceIndex index = PerformanceIndex.forInvestment(getClient(), getDashboardData().getCurrencyConverter(),
                            security, period, new ArrayList<>());

            long[] totals = index.getTotals();
            double[] deltas = index.getDeltaPercentage();

            if (totals == null || totals.length == 0 || deltas == null || deltas.length < 2)
                continue;

            long marketValue = totals[totals.length - 1];
            if (marketValue <= 0)
                continue;

            indices.add(index);
            securities.add(security);
            marketValues.add(marketValue);
        }

        if (indices.size() < 2)
            return List.of();

        double totalMarketValue = marketValues.stream().mapToDouble(Long::doubleValue).sum();
        if (totalMarketValue <= 0)
            return List.of();

        double[] weights = new double[marketValues.size()];
        for (int i = 0; i < marketValues.size(); i++)
            weights[i] = marketValues.get(i) / totalMarketValue;

        Covariance covariance = new Covariance(indices);
        covariance.calculate();

        PortfolioAnalytics analytics = new PortfolioAnalytics(covariance, indices);
        double[] contributions = analytics.getRiskContributionsPercentage(weights);

        List<RiskContributionItem> items = new ArrayList<>();
        for (int i = 0; i < contributions.length; i++)
        {
            double contribution = contributions[i];
            if (!Double.isFinite(contribution) || contribution <= 0)
                continue;

            Security security = securities.get(i);
            String id = security.getUUID() != null ? security.getUUID() : Integer.toString(i);
            items.add(new RiskContributionItem(id, security.getName(), contribution));
        }

        if (items.isEmpty())
            return List.of();

        double sum = items.stream().mapToDouble(RiskContributionItem::contribution).sum();
        if (sum <= 0)
            return List.of();

        return items.stream()
                        .map(item -> new RiskContributionItem(item.id(), item.label(), item.contribution() / sum))
                        .sorted(Comparator.comparingDouble(RiskContributionItem::contribution).reversed())
                        .toList();
    }

    private List<Security> collectCandidateSecurities(DataSeries dataSeries)
    {
        Set<Security> securities = new LinkedHashSet<>();
        Client filteredClient = get(ClientFilterConfig.class).getSelectedFilter().filter(getClient());

        switch (dataSeries.getType())
        {
            case SECURITY:
                securities.add((Security) dataSeries.getInstance());
                break;
            case PORTFOLIO:
            case PORTFOLIO_PRETAX:
            case PORTFOLIO_PLUS_ACCOUNT:
            case PORTFOLIO_PLUS_ACCOUNT_PRETAX:
                Portfolio portfolio = (Portfolio) dataSeries.getInstance();
                portfolio.getTransactions().stream().map(t -> t.getSecurity()).filter(java.util.Objects::nonNull)
                                .forEach(securities::add);
                break;
            default:
                securities.addAll(filteredClient.getSecurities());
                break;
        }

        return new ArrayList<>(securities);
    }

    private static boolean isSupportedDataSeries(DataSeries series)
    {
        return switch (series.getType())
        {
            case CLIENT:
            case CLIENT_PRETAX:
            case SECURITY:
            case PORTFOLIO:
            case PORTFOLIO_PRETAX:
            case PORTFOLIO_PLUS_ACCOUNT:
            case PORTFOLIO_PLUS_ACCOUNT_PRETAX:
                yield true;
            default:
                yield false;
        };
    }

    @Override
    protected void createCircularSeries(List<RiskContributionItem> items)
    {
        ICircularSeries<?> circularSeries = (ICircularSeries<?>) getChart().getSeriesSet().createSeries(SeriesType.DOUGHNUT,
                        Messages.LabelRiskContributionChart);
        circularSeries.setSliceColor(getChart().getPlotArea().getBackground());

        if (items == null || items.isEmpty())
        {
            circularSeries.setSeries(new String[] { Messages.LabelErrorNoRiskContributionData }, new double[] { 100 });
            circularSeries.setColor(Messages.LabelErrorNoRiskContributionData, Colors.LIGHT_GRAY);
            return;
        }

        Node rootNode = circularSeries.getRootNode();
        CircularChart.PieColors colorWheel = new CircularChart.PieColors();

        for (RiskContributionItem item : items)
        {
            Node node = rootNode.addChild(item.id(), item.contribution());
            node.setData(item);
            circularSeries.setColor(item.id(), colorWheel.next());
        }
    }
}
