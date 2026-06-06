package name.abuchen.portfolio.ui.views;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.layout.TableColumnLayout;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Spinner;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.TabItem;
import org.eclipse.swt.widgets.Text;

import name.abuchen.portfolio.model.ClientProperties;
import name.abuchen.portfolio.model.Security;
import name.abuchen.portfolio.model.SecurityPrice;
import name.abuchen.portfolio.money.CurrencyConverter;
import name.abuchen.portfolio.money.CurrencyConverterImpl;
import name.abuchen.portfolio.money.ExchangeRateProviderFactory;
import name.abuchen.portfolio.rebalance.ConstraintLayer;
import name.abuchen.portfolio.rebalance.DecisionLogger;
import name.abuchen.portfolio.rebalance.IntegrityLayer;
import name.abuchen.portfolio.rebalance.Order;
import name.abuchen.portfolio.rebalance.RebalancingConfig;
import name.abuchen.portfolio.rebalance.RebalancingConfig.Strategy;
import name.abuchen.portfolio.rebalance.RebalancingContext;
import name.abuchen.portfolio.rebalance.RebalancingEngine;
import name.abuchen.portfolio.rebalance.RebalancingMetrics;
import name.abuchen.portfolio.rebalance.RebalancingMetricsCalculator;
import name.abuchen.portfolio.rebalance.RedundancyLayer;
import name.abuchen.portfolio.rebalance.RiskLayer;
import name.abuchen.portfolio.rebalance.ViabilityLayer;
import name.abuchen.portfolio.snapshot.PerformanceIndex;
import name.abuchen.portfolio.ui.Messages;
import name.abuchen.portfolio.ui.dialogs.ListSelectionDialog;
import name.abuchen.portfolio.util.Interval;

public class SmartRebalancerView extends AbstractHistoricView
{
    private static final String PREF_STRATEGY = "rebalancer.strategy"; //$NON-NLS-1$
    private static final String PREF_MAX_WEIGHT = "rebalancer.maxWeight"; //$NON-NLS-1$
    private static final String PREF_MIN_WEIGHT = "rebalancer.minWeight"; //$NON-NLS-1$
    private static final String PREF_CORRELATION = "rebalancer.correlation"; //$NON-NLS-1$
    private static final String PREF_INERTIA = "rebalancer.inertiaTolerance"; //$NON-NLS-1$
    private static final String PREF_MAX_VOL = "rebalancer.maxVolatility"; //$NON-NLS-1$
    private static final String PREF_FIXED_FEE = "rebalancer.commissionFixed"; //$NON-NLS-1$
    private static final String PREF_VAR_FEE = "rebalancer.commissionVariable"; //$NON-NLS-1$
    private static final String PREF_CASH_BUFFER = "rebalancer.cashBuffer"; //$NON-NLS-1$
    private static final String PREF_NEW_CAPITAL = "rebalancer.newCapital"; //$NON-NLS-1$
    private static final String PREF_ALLOW_FRACTIONS = "rebalancer.allowFractions"; //$NON-NLS-1$
    private static final String PREF_MONITORING = "rebalancer.monitoringInterval"; //$NON-NLS-1$
    private static final String PREF_FROZEN = "rebalancer.frozenAssets"; //$NON-NLS-1$

    private Combo strategyCombo;
    private Spinner maxWeightSpinner;
    private Spinner minWeightSpinner;
    private Spinner correlationSpinner;
    private Spinner inertiaSpinner;
    private Spinner maxVolSpinner;
    private Text fixedFeeText;
    private Text varFeeText;
    private Text cashBufferText;
    private Text newCapitalText;
    private Spinner monitoringSpinner;
    private Button allowFractionsCheck;
    private org.eclipse.swt.widgets.List frozenList;

    private Label statusLabel;
    private Label calculationDateLabel;
    private TableViewer ordersViewer;
    private TableViewer impactViewer;
    private Text logText;

    private Label summaryTotalValue;
    private Label summaryNewCapital;
    private Label summaryProjectedValue;
    private Label summaryEstimatedFees;
    private Label summaryRemainingCash;

    private Label metricSharpeHistorical;
    private Label metricSharpeUnchanged;
    private Label metricSharpeChanged;
    private Label metricVolatilityHistorical;
    private Label metricVolatilityUnchanged;
    private Label metricVolatilityChanged;
    private Label metricVaRHistorical;
    private Label metricVaRUnchanged;
    private Label metricVaRChanged;

    private final java.util.IdentityHashMap<PerformanceIndex, String>   assetNameMap     = new java.util.IdentityHashMap<>();
    private final java.util.IdentityHashMap<PerformanceIndex, Security> assetSecurityMap = new java.util.IdentityHashMap<>();

    private Color colorGreen;
    private Color colorAmber;
    private Color colorRed;

    private PerformanceIndex portfolioIndex;

    private List<PerformanceIndex> cachedAssets;
    private double[] cachedPrices;
    private double[] cachedWeights;
    private double cachedTotalValue;
    private double cachedTotalCash;
    private Interval cachedInterval;

    @Override
    protected String getDefaultTitle()
    {
        return Messages.LabelSmartRebalancer;
    }

    @Override
    protected Control createBody(Composite parent)
    {
        if (colorGreen == null || colorGreen.isDisposed())
            colorGreen = new Color(Display.getDefault(), 56, 142, 60);
        if (colorAmber == null || colorAmber.isDisposed())
            colorAmber = new Color(Display.getDefault(), 245, 127, 23);
        if (colorRed == null || colorRed.isDisposed())
            colorRed   = new Color(Display.getDefault(), 198, 40, 40);

        parent.addDisposeListener(e -> {
            if (colorGreen != null && !colorGreen.isDisposed()) colorGreen.dispose();
            if (colorAmber != null && !colorAmber.isDisposed()) colorAmber.dispose();
            if (colorRed   != null && !colorRed.isDisposed())   colorRed.dispose();
        });

        SashForm sash = new SashForm(parent, SWT.HORIZONTAL);
        sash.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        createConfigPanel(sash);
        createResultsPanel(sash);

        sash.setWeights(new int[] { 28, 72 });

        loadPreferences();

        Display.getDefault().asyncExec(() -> {
            if (!sash.isDisposed())
                runAnalysis();
        });

        return sash;
    }

    private void createConfigPanel(Composite parent)
    {
        ScrolledComposite scroll = new ScrolledComposite(parent, SWT.V_SCROLL | SWT.BORDER);
        scroll.setExpandHorizontal(true);
        scroll.setExpandVertical(true);

        Composite panel = new Composite(scroll, SWT.NONE);
        GridLayoutFactory.fillDefaults().margins(8, 8).spacing(5, 5).applyTo(panel);

        Composite strategyHeader = new Composite(panel, SWT.NONE);
        GridLayoutFactory.fillDefaults().numColumns(2).applyTo(strategyHeader);
        GridDataFactory.fillDefaults().grab(true, false).applyTo(strategyHeader);

        Label strategyLabel = new Label(strategyHeader, SWT.NONE);
        strategyLabel.setText(Messages.LabelRebalancerStrategy);
        GridDataFactory.fillDefaults().grab(true, false).align(SWT.LEFT, SWT.CENTER).applyTo(strategyLabel);

        Button resetBtn = new Button(strategyHeader, SWT.PUSH);
        resetBtn.setText(Messages.LabelRebalancerResetDefaults);
        GridDataFactory.fillDefaults().grab(false, false).align(SWT.RIGHT, SWT.CENTER).applyTo(resetBtn);
        resetBtn.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                resetToDefaults();
            }
        });

        createStrategyGroup(panel);

        createSectionLabel(panel, Messages.LabelRebalancerLimitsFilters);
        createLimitsGroup(panel);

        createSectionLabel(panel, Messages.LabelRebalancerCostsCapital);
        createCostsGroup(panel);

        createSectionLabel(panel, Messages.LabelRebalancerFrozenAssets);
        createFrozenGroup(panel);

        Button analyzeBtn = new Button(panel, SWT.PUSH);
        analyzeBtn.setText(Messages.LabelRebalancerAnalyze);
        analyzeBtn.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        analyzeBtn.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                runAnalysis();
            }
        });

        scroll.setContent(panel);
        scroll.setMinSize(panel.computeSize(SWT.DEFAULT, SWT.DEFAULT));
    }

    private void createSectionLabel(Composite parent, String text)
    {
        Label lbl = new Label(parent, SWT.NONE);
        lbl.setText(text);
        GridDataFactory.fillDefaults().grab(true, false).applyTo(lbl);
    }

    private void createStrategyGroup(Composite parent)
    {
        Group g = new Group(parent, SWT.NONE);
        GridLayoutFactory.fillDefaults().numColumns(2).margins(6, 6).applyTo(g);
        GridDataFactory.fillDefaults().grab(true, false).applyTo(g);

        new Label(g, SWT.NONE).setText(Messages.LabelRebalancerStrategy);
        strategyCombo = new Combo(g, SWT.READ_ONLY | SWT.DROP_DOWN);
        strategyCombo.setItems(Messages.LabelRebalancerMaxSharpe, Messages.LabelRebalancerMinVolatility);
        strategyCombo.select(0);
        GridDataFactory.fillDefaults().grab(true, false).applyTo(strategyCombo);

        new Label(g, SWT.NONE).setText(Messages.LabelRebalancerMonitoringInterval);
        monitoringSpinner = new Spinner(g, SWT.BORDER);
        monitoringSpinner.setMinimum(1);
        monitoringSpinner.setMaximum(44640);
        monitoringSpinner.setSelection(1440);
        GridDataFactory.fillDefaults().grab(true, false).applyTo(monitoringSpinner);
    }

    private void createLimitsGroup(Composite parent)
    {
        Group g = new Group(parent, SWT.NONE);
        GridLayoutFactory.fillDefaults().numColumns(2).margins(6, 6).applyTo(g);
        GridDataFactory.fillDefaults().grab(true, false).applyTo(g);

        new Label(g, SWT.NONE).setText(Messages.LabelRebalancerMaxWeight);
        maxWeightSpinner = createPercentSpinner(g, 50);

        new Label(g, SWT.NONE).setText(Messages.LabelRebalancerMinWeight);
        minWeightSpinner = createPercentSpinner(g, 0);

        new Label(g, SWT.NONE).setText(Messages.LabelRebalancerCorrelation);
        correlationSpinner = createPercentSpinner(g, 85);

        new Label(g, SWT.NONE).setText(Messages.LabelRebalancerDriftTolerance);
        inertiaSpinner = createPercentSpinner(g, 2);

        new Label(g, SWT.NONE).setText(Messages.LabelRebalancerMaxVolatility);
        maxVolSpinner = createPercentSpinner(g, 100);

        allowFractionsCheck = new Button(g, SWT.CHECK);
        allowFractionsCheck.setText(Messages.LabelRebalancerAllowFractions);
        GridDataFactory.fillDefaults().span(2, 1).applyTo(allowFractionsCheck);
    }

    private void createCostsGroup(Composite parent)
    {
        Group g = new Group(parent, SWT.NONE);
        GridLayoutFactory.fillDefaults().numColumns(2).margins(6, 6).applyTo(g);
        GridDataFactory.fillDefaults().grab(true, false).applyTo(g);

        new Label(g, SWT.NONE).setText(Messages.LabelRebalancerFixedCommission);
        fixedFeeText = createNumberText(g, "0.00"); //$NON-NLS-1$

        new Label(g, SWT.NONE).setText(Messages.LabelRebalancerVariableCommission);
        varFeeText = createNumberText(g, "0.00"); //$NON-NLS-1$

        new Label(g, SWT.NONE).setText(Messages.LabelRebalancerCashBuffer);
        cashBufferText = createNumberText(g, "0.00"); //$NON-NLS-1$

        new Label(g, SWT.NONE).setText(Messages.LabelRebalancerNewCapital);
        newCapitalText = createNumberText(g, "0.00"); //$NON-NLS-1$
    }

    private void createFrozenGroup(Composite parent)
    {
        Group g = new Group(parent, SWT.NONE);
        GridLayoutFactory.fillDefaults().numColumns(2).margins(6, 6).applyTo(g);
        GridDataFactory.fillDefaults().grab(true, false).applyTo(g);

        frozenList = new org.eclipse.swt.widgets.List(g, SWT.BORDER | SWT.V_SCROLL | SWT.MULTI);
        GridDataFactory.fillDefaults().span(2, 1).hint(SWT.DEFAULT, 80).grab(true, false).applyTo(frozenList);

        Button addBtn = new Button(g, SWT.PUSH);
        addBtn.setText(Messages.LabelRebalancerAddFrozen);
        addBtn.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                ListSelectionDialog dlg = new ListSelectionDialog(getActiveShell(),
                                new org.eclipse.jface.viewers.LabelProvider()
                                {
                                    @Override
                                    public String getText(Object element)
                                    {
                                        if (element instanceof Security)
                                            return ((Security) element).getName();
                                        return super.getText(element);
                                    }
                                });
                dlg.setTitle(Messages.LabelRebalancerFrozenAssets);
                dlg.setMessage(Messages.LabelRebalancerEnterFrozen);
                dlg.setElements(getClient().getSecurities());
                dlg.setMultiSelection(true);

                if (dlg.open() == Window.OK)
                {
                    for (Object obj : dlg.getResult())
                    {
                        if (obj instanceof Security)
                        {
                            String name = ((Security) obj).getName();
                            boolean exists = false;
                            for (String item : frozenList.getItems())
                            {
                                if (item.equals(name))
                                {
                                    exists = true;
                                    break;
                                }
                            }
                            if (!exists)
                                frozenList.add(name);
                        }
                    }
                }
            }
        });

        Button removeBtn = new Button(g, SWT.PUSH);
        removeBtn.setText(Messages.LabelRebalancerRemoveFrozen);
        removeBtn.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                frozenList.remove(frozenList.getSelectionIndices());
            }
        });
    }

    private void createResultsPanel(Composite parent)
    {
        Composite panel = new Composite(parent, SWT.NONE);
        GridLayoutFactory.fillDefaults().margins(8, 8).spacing(5, 8).applyTo(panel);

        Composite statusBar = new Composite(panel, SWT.NONE);
        GridLayoutFactory.fillDefaults().numColumns(3).applyTo(statusBar);
        GridDataFactory.fillDefaults().grab(true, false).applyTo(statusBar);

        statusLabel = new Label(statusBar, SWT.CENTER);
        statusLabel.setText(Messages.LabelRebalancerNoResults);
        GridDataFactory.fillDefaults().align(SWT.FILL, SWT.CENTER).grab(true, false).applyTo(statusLabel);

        statusLabel.addPaintListener(e -> {
            if (!statusLabel.isDisposed())
            {
                org.eclipse.swt.graphics.Color bg = (org.eclipse.swt.graphics.Color) statusLabel.getData("current_bg");
                org.eclipse.swt.graphics.Color fg = (org.eclipse.swt.graphics.Color) statusLabel.getData("current_fg");

                if (bg != null)
                {
                    e.gc.setBackground(bg);
                    statusLabel.setBackground(bg);
                }
                if (fg != null)
                {
                    e.gc.setForeground(fg);
                    statusLabel.setForeground(fg);
                }
            }
        });

        new Label(statusBar, SWT.NONE).setText(Messages.LabelRebalancerCalculationDate + ":"); //$NON-NLS-1$
        calculationDateLabel = new Label(statusBar, SWT.NONE);
        calculationDateLabel.setText("-"); //$NON-NLS-1$

        Group summaryGroup = new Group(panel, SWT.NONE);
        summaryGroup.setText(Messages.LabelRebalancerSummaryTitle);
        GridLayoutFactory.fillDefaults().numColumns(5).equalWidth(true).margins(8, 6).spacing(10, 5)
                        .applyTo(summaryGroup);
        GridDataFactory.fillDefaults().grab(true, false).align(SWT.FILL, SWT.CENTER).applyTo(summaryGroup);

        String currency    = getClient().getBaseCurrency();
        String defaultEmpty = "- " + currency; //$NON-NLS-1$

        summaryTotalValue    = createSummaryCard(summaryGroup, Messages.LabelRebalancerCurrentValue,   defaultEmpty);
        summaryNewCapital    = createSummaryCard(summaryGroup, Messages.LabelRebalancerNewCapital,      defaultEmpty);
        summaryProjectedValue= createSummaryCard(summaryGroup, Messages.LabelRebalancerProjectedValue, defaultEmpty);
        summaryEstimatedFees = createSummaryCard(summaryGroup, Messages.LabelRebalancerEstimatedFees,  defaultEmpty);
        summaryRemainingCash = createSummaryCard(summaryGroup, Messages.LabelRebalancerRemainingCash,  defaultEmpty);

        Group metricsGroup = new Group(panel, SWT.NONE);
        metricsGroup.setText(Messages.LabelRebalancerRiskMetricsTitle);
        GridLayoutFactory.fillDefaults().numColumns(4).equalWidth(true).margins(8, 6).spacing(4, 4)
                        .applyTo(metricsGroup);
        GridDataFactory.fillDefaults().grab(true, false).align(SWT.FILL, SWT.CENTER).applyTo(metricsGroup);

        new Label(metricsGroup, SWT.NONE).setText(""); //$NON-NLS-1$

        Label hSharpe = new Label(metricsGroup, SWT.NONE);
        hSharpe.setText(Messages.LabelRebalancerMetricSharpe);
        GridDataFactory.fillDefaults().grab(true, false).align(SWT.CENTER, SWT.CENTER).applyTo(hSharpe);

        Label hVol = new Label(metricsGroup, SWT.NONE);
        hVol.setText(Messages.LabelRebalancerMetricVolatility);
        GridDataFactory.fillDefaults().grab(true, false).align(SWT.CENTER, SWT.CENTER).applyTo(hVol);

        Label hVaR = new Label(metricsGroup, SWT.NONE);
        hVaR.setText(Messages.LabelRebalancerMetricVaR);
        GridDataFactory.fillDefaults().grab(true, false).align(SWT.CENTER, SWT.CENTER).applyTo(hVaR);

        Label rowHistorical = new Label(metricsGroup, SWT.NONE);
        rowHistorical.setText(Messages.LabelRebalancerRowHistorical);
        GridDataFactory.fillDefaults().grab(true, false).align(SWT.LEFT, SWT.CENTER).applyTo(rowHistorical);
        metricSharpeHistorical = createMetricLabel(metricsGroup);
        metricVolatilityHistorical = createMetricLabel(metricsGroup);
        metricVaRHistorical = createMetricLabel(metricsGroup);

        Label rowUnchanged = new Label(metricsGroup, SWT.NONE);
        rowUnchanged.setText(Messages.LabelRebalancerRowUnchanged);
        GridDataFactory.fillDefaults().grab(true, false).align(SWT.LEFT, SWT.CENTER).applyTo(rowUnchanged);
        metricSharpeUnchanged = createMetricLabel(metricsGroup);
        metricVolatilityUnchanged = createMetricLabel(metricsGroup);
        metricVaRUnchanged = createMetricLabel(metricsGroup);

        Label rowRebalanced = new Label(metricsGroup, SWT.NONE);
        rowRebalanced.setText(Messages.LabelRebalancerRowChanged);
        GridDataFactory.fillDefaults().grab(true, false).align(SWT.LEFT, SWT.CENTER).applyTo(rowRebalanced);
        metricSharpeChanged = createColorPersistentMetricLabel(metricsGroup);
        metricVolatilityChanged = createColorPersistentMetricLabel(metricsGroup);
        metricVaRChanged = createColorPersistentMetricLabel(metricsGroup);

        SashForm verticalSash = new SashForm(panel, SWT.VERTICAL);
        GridDataFactory.fillDefaults().grab(true, true).applyTo(verticalSash);

        TabFolder tabFolder = new TabFolder(verticalSash, SWT.NONE);
        GridDataFactory.fillDefaults().grab(true, true).applyTo(tabFolder);

        TabItem ordersTab = new TabItem(tabFolder, SWT.NONE);
        ordersTab.setText(Messages.LabelRebalancerProposedOrders);
        Composite ordersTabComposite = new Composite(tabFolder, SWT.NONE);
        GridLayoutFactory.fillDefaults().margins(4, 4).applyTo(ordersTabComposite);
        createOrdersTable(ordersTabComposite);
        ordersTab.setControl(ordersTabComposite);

        TabItem impactTab = new TabItem(tabFolder, SWT.NONE);
        impactTab.setText(Messages.LabelRebalancerProjectedImpact);
        Composite impactTabComposite = new Composite(tabFolder, SWT.NONE);
        GridLayoutFactory.fillDefaults().margins(4, 4).applyTo(impactTabComposite);
        createImpactTable(impactTabComposite);
        impactTab.setControl(impactTabComposite);

        Group logGroup = new Group(verticalSash, SWT.NONE);
        logGroup.setText(Messages.LabelRebalancerDecisionLog);
        GridLayoutFactory.fillDefaults().applyTo(logGroup);

        logText = new Text(logGroup, SWT.BORDER | SWT.MULTI | SWT.READ_ONLY | SWT.V_SCROLL | SWT.WRAP);
        logText.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        logText.setText(Messages.LabelRebalancerNoResults);

        verticalSash.setWeights(new int[] { 70, 30 });
    }

    private Label createSummaryCard(Composite parent, String labelText, String initialValue)
    {
        Composite card = new Composite(parent, SWT.NONE);
        GridLayoutFactory.fillDefaults().applyTo(card);
        GridDataFactory.fillDefaults().grab(true, false).align(SWT.FILL, SWT.CENTER).applyTo(card);

        Label lbl = new Label(card, SWT.NONE);
        lbl.setText(labelText);
        GridDataFactory.fillDefaults().grab(true, false).align(SWT.FILL, SWT.CENTER).applyTo(lbl);

        Label value = new Label(card, SWT.NONE);
        value.setText(initialValue);
        GridDataFactory.fillDefaults().grab(true, false).align(SWT.FILL, SWT.CENTER).applyTo(value);

        return value;
    }

    private Label createMetricLabel(Composite parent)
    {
        Label lbl = new Label(parent, SWT.NONE);
        lbl.setText("-"); //$NON-NLS-1$
        GridDataFactory.fillDefaults().grab(true, false).align(SWT.CENTER, SWT.CENTER).applyTo(lbl);
        return lbl;
    }

    private Label createColorPersistentMetricLabel(Composite parent)
    {
        Label lbl = new Label(parent, SWT.NONE);
        lbl.setText("-"); //$NON-NLS-1$
        GridDataFactory.fillDefaults().grab(true, false).align(SWT.CENTER, SWT.CENTER).applyTo(lbl);

        lbl.addPaintListener(e -> {
            if (!lbl.isDisposed())
            {
                Color fg = (Color) lbl.getData("metric_fg"); //$NON-NLS-1$
                if (fg != null && !fg.isDisposed())
                    lbl.setForeground(fg);
            }
        });

        return lbl;
    }

    private void createOrdersTable(Composite parent)
    {
        Composite tableContainer = new Composite(parent, SWT.NONE);
        tableContainer.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        tableContainer.setLayout(new TableColumnLayout());

        ordersViewer = new TableViewer(tableContainer,
                        SWT.BORDER | SWT.FULL_SELECTION | SWT.H_SCROLL | SWT.V_SCROLL);
        ordersViewer.getTable().setHeaderVisible(true);
        ordersViewer.getTable().setLinesVisible(true);
        ordersViewer.setContentProvider(ArrayContentProvider.getInstance());

        createColumn(ordersViewer, Messages.ColumnSecurity, 140,
                        o -> ((Order) o).getAsset() != null ? getAssetDisplayName((Order) o) : "-", //$NON-NLS-1$
                        null);

        createColumn(ordersViewer, Messages.ColumnAction, 60,
                        o -> ((Order) o).getSide() == Order.OrderSide.BUY ? "BUY" : "SELL", //$NON-NLS-1$ //$NON-NLS-2$
                        o -> ((Order) o).getSide() == Order.OrderSide.BUY
                                        ? Display.getCurrent().getSystemColor(SWT.COLOR_DARK_GREEN)
                                        : Display.getCurrent().getSystemColor(SWT.COLOR_RED));

        createColumn(ordersViewer, Messages.LabelRebalancerCurrentWeight, 80,
                        o -> {
                            RebalancingContext ctx = (RebalancingContext) ordersViewer.getData("ctx"); //$NON-NLS-1$
                            if (ctx != null && ((Order) o).getAsset() != null)
                            {
                                int idx = ctx.getAssets().indexOf(((Order) o).getAsset());
                                if (idx >= 0 && ctx.getCurrentWeights() != null)
                                    return String.format("%.2f%%", ctx.getCurrentWeights()[idx] * 100); //$NON-NLS-1$
                            }
                            return "-"; //$NON-NLS-1$
                        }, null);

        createColumn(ordersViewer, Messages.LabelRebalancerTargetWeight, 80,
                        o -> {
                            RebalancingContext ctx = (RebalancingContext) ordersViewer.getData("ctx"); //$NON-NLS-1$
                            if (ctx != null && ((Order) o).getAsset() != null)
                            {
                                int idx = ctx.getAssets().indexOf(((Order) o).getAsset());
                                if (idx >= 0 && ctx.getFinalWeights() != null)
                                {
                                    double weightPercent = ctx.getFinalWeights()[idx] * 100.0;
                                    if (Math.abs(weightPercent) < 1e-6)
                                    {
                                        weightPercent = 0.0;
                                    }

                                    return String.format("%.2f%%", weightPercent); //$NON-NLS-1$
                                }
                            }
                            return "-"; //$NON-NLS-1$
                        }, 
                        o -> {
                            Order order = (Order) o;
                            return order.getSide() == Order.OrderSide.BUY
                                            ? Display.getCurrent().getSystemColor(SWT.COLOR_DARK_GREEN)
                                            : Display.getCurrent().getSystemColor(SWT.COLOR_RED);
                        });

        createColumn(ordersViewer, Messages.LabelRebalancerQuantity, 80,
                        o -> {
                            double qty = ((Order) o).getQuantity();
                            return allowFractionsCheck.getSelection()
                                            ? String.format("%,.2f", qty) //$NON-NLS-1$
                                            : String.format("%,.0f", qty); //$NON-NLS-1$
                        }, null);

        createColumn(ordersViewer, Messages.LabelRebalancerEstimatedPrice, 90,
                        o -> String.format("%.2f", ((Order) o).getEstimatedPrice()), //$NON-NLS-1$
                        null);

        createColumn(ordersViewer, Messages.LabelRebalancerTotalValue, 90,
                        o -> String.format("%,.2f", ((Order) o).getTotalValue()), //$NON-NLS-1$
                        null);
    }

    private void createImpactTable(Composite parent)
    {
        Composite tableContainer = new Composite(parent, SWT.NONE);
        tableContainer.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        tableContainer.setLayout(new TableColumnLayout());

        impactViewer = new TableViewer(tableContainer,
                        SWT.BORDER | SWT.FULL_SELECTION | SWT.H_SCROLL | SWT.V_SCROLL);
        impactViewer.getTable().setHeaderVisible(true);
        impactViewer.getTable().setLinesVisible(true);
        impactViewer.setContentProvider(ArrayContentProvider.getInstance());

        createColumn(impactViewer, Messages.ColumnSecurity, 140,
                        o -> assetNameMap.getOrDefault((PerformanceIndex) o, "-"), //$NON-NLS-1$
                        null);

        createColumn(impactViewer, Messages.LabelRebalancerCurrentWeight, 80,
                        o -> {
                            RebalancingContext ctx = (RebalancingContext) impactViewer.getData("ctx"); //$NON-NLS-1$
                            if (ctx != null)
                            {
                                int idx = ctx.getAssets().indexOf(o);
                                if (idx >= 0 && ctx.getCurrentWeights() != null)
                                    return String.format("%.2f%%", ctx.getCurrentWeights()[idx] * 100); //$NON-NLS-1$
                            }
                            return "-"; //$NON-NLS-1$
                        }, null);

        createColumn(impactViewer, Messages.LabelRebalancerCurrentValue, 100,
                        o -> {
                            RebalancingContext ctx = (RebalancingContext) impactViewer.getData("ctx"); //$NON-NLS-1$
                            if (ctx != null)
                            {
                                int idx = ctx.getAssets().indexOf(o);
                                if (idx >= 0 && ctx.getCurrentWeights() != null)
                                {
                                    double val = ctx.getCurrentWeights()[idx] * ctx.getTotalPortfolioValue();
                                    return String.format("%,.2f %s", val, getClient().getBaseCurrency()); //$NON-NLS-1$
                                }
                            }
                            return "-"; //$NON-NLS-1$
                        }, null);

        createColumn(impactViewer, Messages.LabelRebalancerChange, 80,
                        o -> {
                            RebalancingContext ctx = (RebalancingContext) impactViewer.getData("ctx"); //$NON-NLS-1$
                            if (ctx != null)
                            {
                                int idx = ctx.getAssets().indexOf(o);
                                if (idx >= 0 && ctx.getCurrentWeights() != null && ctx.getFinalWeights() != null)
                                {
                                    double diff = (ctx.getFinalWeights()[idx] - ctx.getCurrentWeights()[idx]) * 100;
                                    return String.format("%+.2f%%", diff); //$NON-NLS-1$
                                }
                            }
                            return "-"; //$NON-NLS-1$
                        }, o -> {
                            RebalancingContext ctx = (RebalancingContext) impactViewer.getData("ctx"); //$NON-NLS-1$
                            if (ctx != null && ctx.getCurrentWeights() != null && ctx.getFinalWeights() != null)
                            {
                                int idx = ctx.getAssets().indexOf(o);
                                if (idx >= 0)
                                {
                                    double diff = ctx.getFinalWeights()[idx] - ctx.getCurrentWeights()[idx];
                                    if (diff > 0.0001)
                                        return Display.getCurrent().getSystemColor(SWT.COLOR_DARK_GREEN);
                                    if (diff < -0.0001)
                                        return Display.getCurrent().getSystemColor(SWT.COLOR_RED);
                                }
                            }
                            return null;
                        });

        createColumn(impactViewer, Messages.LabelRebalancerTargetWeight, 80,
                        o -> {
                            RebalancingContext ctx = (RebalancingContext) impactViewer.getData("ctx"); //$NON-NLS-1$
                            if (ctx != null)
                            {
                                int idx = ctx.getAssets().indexOf(o);
                                if (idx >= 0 && ctx.getFinalWeights() != null)
                                    return String.format("%.2f%%", ctx.getFinalWeights()[idx] * 100); //$NON-NLS-1$
                            }
                            return "-"; //$NON-NLS-1$
                        }, o -> {
                            RebalancingContext ctx = (RebalancingContext) impactViewer.getData("ctx"); //$NON-NLS-1$
                            if (ctx != null && ctx.getCurrentWeights() != null && ctx.getFinalWeights() != null)
                            {
                                int idx = ctx.getAssets().indexOf(o);
                                if (idx >= 0)
                                {
                                    return ctx.getFinalWeights()[idx] < ctx.getCurrentWeights()[idx]
                                                    ? Display.getCurrent().getSystemColor(SWT.COLOR_RED)
                                                    : Display.getCurrent().getSystemColor(SWT.COLOR_DARK_GREEN);
                                }
                            }
                            return null;
                        });

        createColumn(impactViewer, Messages.LabelRebalancerProjectedValue, 100,
                        o -> {
                            RebalancingContext ctx = (RebalancingContext) impactViewer.getData("ctx"); //$NON-NLS-1$
                            if (ctx != null)
                            {
                                int idx = ctx.getAssets().indexOf(o);
                                if (idx >= 0 && ctx.getFinalWeights() != null)
                                {
                                    double investable = ctx.getTotalPortfolioValue()
                                                    + ctx.getConfig().getNewCashAmount()
                                                    - ctx.getConfig().getCashBuffer();
                                    double val = ctx.getFinalWeights()[idx] * investable;
                                    return String.format("%,.2f %s", val, getClient().getBaseCurrency()); //$NON-NLS-1$
                                }
                            }
                            return "-"; //$NON-NLS-1$
                        }, o -> {
                            RebalancingContext ctx = (RebalancingContext) impactViewer.getData("ctx"); //$NON-NLS-1$
                            if (ctx != null && ctx.getCurrentWeights() != null && ctx.getFinalWeights() != null)
                            {
                                int idx = ctx.getAssets().indexOf(o);
                                if (idx >= 0)
                                {
                                    return ctx.getFinalWeights()[idx] < ctx.getCurrentWeights()[idx]
                                                    ? Display.getCurrent().getSystemColor(SWT.COLOR_RED)
                                                    : Display.getCurrent().getSystemColor(SWT.COLOR_DARK_GREEN);
                                }
                            }
                            return null;
                        });

        createColumn(impactViewer, Messages.ColumnStatus, 80,
                        o -> {
                            RebalancingContext ctx = (RebalancingContext) impactViewer.getData("ctx"); //$NON-NLS-1$
                            if (ctx != null)
                            {
                                int idx = ctx.getAssets().indexOf(o);
                                if (idx >= 0)
                                {
                                    String id = ctx.getAssetIdentifier(idx);
                                    if (id != null && ctx.getConfig().isAssetFrozen(id))
                                        return Messages.LabelRebalancerStatusFrozen;
                                    return Messages.LabelRebalancerStatusNormal;
                                }
                            }
                            return "-"; //$NON-NLS-1$
                        }, o -> {
                            RebalancingContext ctx = (RebalancingContext) impactViewer.getData("ctx"); //$NON-NLS-1$
                            if (ctx != null)
                            {
                                int idx = ctx.getAssets().indexOf(o);
                                if (idx >= 0)
                                {
                                    String id = ctx.getAssetIdentifier(idx);
                                    if (id != null && ctx.getConfig().isAssetFrozen(id))
                                        return Display.getCurrent().getSystemColor(SWT.COLOR_BLUE);
                                }
                            }
                            return null;
                        });
    }

    private void createColumn(TableViewer viewer, String header, int width,
                    java.util.function.Function<Object, String> labelFn,
                    java.util.function.Function<Object, Color> colorFn)
    {
        TableViewerColumn col = new TableViewerColumn(viewer, SWT.NONE);
        col.getColumn().setText(header);
        col.getColumn().setWidth(width);

        Composite tableParent = viewer.getTable().getParent();
        if (tableParent.getLayout() instanceof TableColumnLayout)
            ((TableColumnLayout) tableParent.getLayout())
                .setColumnData(col.getColumn(), new ColumnWeightData(10, width, false));

        col.setLabelProvider(new ColumnLabelProvider()
        {
            @Override
            public String getText(Object element)
            {
                return labelFn.apply(element);
            }

            @Override
            public Color getForeground(Object element)
            {
                if (colorFn != null)
                {
                    Color c = colorFn.apply(element);
                    if (c != null)
                        return c;
                }
                return super.getForeground(element);
            }
        });
    }

    private void runAnalysis()
    {
        logText.setText(""); //$NON-NLS-1$

        RebalancingConfig config = buildConfigFromUI();
        savePreferences(config);

        ExchangeRateProviderFactory factory = new ExchangeRateProviderFactory(getClient());
        CurrencyConverter converter = new CurrencyConverterImpl(factory, getClient().getBaseCurrency());
        Interval interval = getReportingPeriod() != null ? getReportingPeriod().toInterval(java.time.LocalDate.now())
                        : Interval.of(java.time.LocalDate.now().minusYears(2), java.time.LocalDate.now());

        boolean cacheValid = cachedInterval != null && cachedInterval.equals(interval) && cachedAssets != null;

        if (!cacheValid)
        {
            assetNameMap.clear();
            assetSecurityMap.clear();

            List<Exception> warnings = new ArrayList<>();
            portfolioIndex = PerformanceIndex.forClient(getClient(), converter, interval, warnings);

            cachedAssets = buildAssetList(config, converter, interval);

            if (cachedAssets.isEmpty())
            {
                setStatus(Messages.LabelRebalancerStatusAmber, colorAmber);
                logText.setText(Messages.LabelRebalancerNoAssets);
                return;
            }

            List<Long> marketValues = new ArrayList<>();
            for (PerformanceIndex pi : cachedAssets)
            {
                long[] totals = pi.getTotals();
                long marketValue = (totals != null && totals.length > 0) ? totals[totals.length - 1] : 0L;
                marketValues.add(marketValue);
            }

            double totalMarketValueRaw = marketValues.stream().mapToDouble(Long::doubleValue).sum();
            double totalAssetsValue = totalMarketValueRaw / name.abuchen.portfolio.money.Values.Amount.divider();

            cachedTotalCash = getClient().getAccounts().stream()
                            .mapToDouble(a -> (double) a.getCurrentAmount(java.time.LocalDateTime.now())
                                            / name.abuchen.portfolio.money.Values.Amount.divider())
                            .sum();

            cachedTotalValue = totalAssetsValue + cachedTotalCash;

            cachedWeights = new double[cachedAssets.size()];
            if (cachedTotalValue > 0)
            {
                for (int i = 0; i < cachedAssets.size(); i++)
                    cachedWeights[i] = (marketValues.get(i) / name.abuchen.portfolio.money.Values.Amount.divider()) / cachedTotalValue;
            }

            cachedPrices = new double[cachedAssets.size()];
            for (int i = 0; i < cachedAssets.size(); i++)
            {
                Security security = assetSecurityMap.get(cachedAssets.get(i));
                double price = 0.0;
                if (security != null)
                {
                    SecurityPrice sp = security.getSecurityPrice(java.time.LocalDate.now());
                    if (sp != null && sp.getValue() > 0)
                        price = (double) sp.getValue() / name.abuchen.portfolio.money.Values.Quote.divider();
                }
                if (price <= 0.0)
                {
                    double[] accumulated = cachedAssets.get(i).getAccumulatedPercentage();
                    if (accumulated != null && accumulated.length > 0 && accumulated[accumulated.length - 1] > 0.0)
                        price = accumulated[accumulated.length - 1];
                }
                cachedPrices[i] = price;
            }

            cachedInterval = interval;
        }

        List<PerformanceIndex> assets = cachedAssets;
        double[] currentWeights = cachedWeights;
        double[] assetPrices = cachedPrices;
        double totalPortfolioValue = cachedTotalValue;
        double totalCash = cachedTotalCash;

        if (assets.isEmpty())
        {
            setStatus(Messages.LabelRebalancerStatusAmber, colorAmber);
            logText.setText(Messages.LabelRebalancerNoAssets);
            return;
        }

        List<String> ids = new ArrayList<>();
        for (PerformanceIndex pi : assets)
            ids.add(assetNameMap.getOrDefault(pi, "?")); //$NON-NLS-1$

        RebalancingContext ctx = new RebalancingContext(assets, ids, config);

        ctx.setCashAccountBalance(totalCash);
        ctx.setTotalPortfolioValue(totalPortfolioValue);
        ctx.setCurrentWeights(currentWeights);
        ctx.setAssetPrices(assetPrices);

        RebalancingEngine engine = new RebalancingEngine();
        engine.addLayer(new IntegrityLayer());
        engine.addLayer(new RiskLayer());
        engine.addLayer(new ConstraintLayer());
        engine.addLayer(new RedundancyLayer());
        engine.addLayer(new ViabilityLayer());

        RebalancingContext resultCtx = engine.evaluateAndExecute(ctx);

        if (!resultCtx.isAborted() && resultCtx.getFinalWeights() == null)
        {
            StringBuilder sb = new StringBuilder();
            for (DecisionLogger.LogEntry entry : resultCtx.getLogger().getLogs())
                sb.append("[").append(entry.getLayerName()).append("] ").append(entry.getMessage()).append("\n");
            logText.setText(sb.toString());
            return;
        }

        if (resultCtx.isAborted())
        {
            setStatus(Messages.LabelRebalancerStatusRed, colorRed);

            StringBuilder sb = new StringBuilder();
            for (DecisionLogger.LogEntry entry : resultCtx.getLogger().getLogs())
                sb.append(String.format("[%s] %s\n", entry.getLayerName(), entry.getMessage())); //$NON-NLS-1$
            logText.setText(sb.toString());

            calculationDateLabel.setText(java.time.LocalDateTime.now()
                            .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)));
            return;
        }

        double rf = new ClientProperties(getClient()).getRiskFreeRateOfReturn();
        if (Double.isNaN(rf))
            rf = 0.0;
        RebalancingMetrics metrics = RebalancingMetricsCalculator.calculate(resultCtx, portfolioIndex, rf);
        displayResults(resultCtx, metrics);
    }

   
    private RebalancingConfig buildConfigFromUI()
    {
        RebalancingConfig config = new RebalancingConfig();
        config.setStrategy(strategyCombo.getSelectionIndex() == 0 ? Strategy.MAX_SHARPE : Strategy.MIN_VOLATILITY);

        double maxW = maxWeightSpinner.getSelection() / 100.0;
        double minW = minWeightSpinner.getSelection() / 100.0;
        if (minW > maxW)
        {
            minW = maxW;
            minWeightSpinner.setSelection(maxWeightSpinner.getSelection());
        }
        config.setWeightLimits(minW, maxW);

        config.setCorrelationThreshold(correlationSpinner.getSelection() / 100.0);
        config.setInertiaTolerance(inertiaSpinner.getSelection() / 100.0);
        config.setMaxPortfolioVolatility(maxVolSpinner.getSelection() / 100.0);
        config.setAllowFractions(allowFractionsCheck.getSelection());
        config.setMonitoringIntervalMinutes(monitoringSpinner.getSelection());
        config.setCommissionFixed(parseDouble(fixedFeeText.getText(), 0.0));
        config.setCommissionVariable(parseDouble(varFeeText.getText(), 0.0) / 100.0);
        config.setCashBuffer(parseDouble(cashBufferText.getText(), 0.0));
        config.setNewCashAmount(parseDouble(newCapitalText.getText(), 0.0));

        for (String item : frozenList.getItems())
            config.addFrozenAsset(item);

        return config;
    }

    private String getAssetDisplayName(Order order)
    {
        if (order == null || order.getAsset() == null)
            return "-"; //$NON-NLS-1$
        String name = assetNameMap.get(order.getAsset());
        return name != null ? name : "-"; //$NON-NLS-1$
    }

    private List<PerformanceIndex> buildAssetList(RebalancingConfig config,
                    CurrencyConverter converter, Interval interval)
    {
        List<PerformanceIndex> result   = new ArrayList<>();
        List<Exception>        warnings = new ArrayList<>();

        for (Security security : getClient().getActiveSecurities())
        {
            try
            {
                PerformanceIndex pi = PerformanceIndex.forInvestment(
                                getClient(), converter, security, interval, warnings);
                if (pi != null && pi.getDeltaPercentage() != null && pi.getDeltaPercentage().length >= 30)
                {
                    result.add(pi);
                    assetNameMap.put(pi, security.getName());
                    assetSecurityMap.put(pi, security);
                }
            }
            catch (Exception ex)
            {
                ex.printStackTrace();
            }
        }
        return result;
    }

    private void displayResults(RebalancingContext ctx, RebalancingMetrics metrics)
    {
        calculationDateLabel.setText(LocalDateTime.now().format(
                        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)));

        if (ctx.isAborted())
            setStatus(Messages.LabelRebalancerStatusRed, colorRed);
        else if (ctx.getProposedOrders() == null || ctx.getProposedOrders().isEmpty())
            setStatus(Messages.LabelRebalancerStatusGreen, colorGreen);
        else
            setStatus(Messages.LabelRebalancerStatusAmber, colorAmber);

        double totalValue    = ctx.getTotalPortfolioValue();
        double newCapital    = ctx.getConfig().getNewCashAmount();
        double cashBuffer    = ctx.getConfig().getCashBuffer();
        double projectedValue = totalValue + newCapital - cashBuffer;

        double existingCash = cachedTotalCash;

        double totalCommissions = 0.0;
        double netInvested      = 0.0;
        if (ctx.getProposedOrders() != null)
        {
            for (Order order : ctx.getProposedOrders())
            {
                double tradeValue = order.getQuantity() * order.getEstimatedPrice();
                double comm       = ctx.getConfig().getCommissionFixed()
                                + (tradeValue * ctx.getConfig().getCommissionVariable());
                totalCommissions += comm;
                if (order.getSide() == Order.OrderSide.BUY)
                    netInvested += tradeValue;
                else
                    netInvested -= tradeValue;
            }
        }
        double remainingCash = (existingCash + newCapital) - netInvested - totalCommissions;
        if (Math.abs(remainingCash) < 0.005)
            remainingCash = 0.0;

        String currency = getClient().getBaseCurrency();
        summaryTotalValue.setText(String.format("%,.2f %s", totalValue, currency)); //$NON-NLS-1$
        summaryNewCapital.setText(String.format("%,.2f %s", newCapital, currency)); //$NON-NLS-1$
        summaryProjectedValue.setText(String.format("%,.2f %s", projectedValue, currency)); //$NON-NLS-1$
        summaryEstimatedFees.setText(String.format("%,.2f %s", totalCommissions, currency)); //$NON-NLS-1$
        summaryRemainingCash.setText(String.format("%,.2f %s", remainingCash, currency)); //$NON-NLS-1$

        metricSharpeHistorical.setText(formatNumber(metrics.getHistoricalSharpe()));
        metricVolatilityHistorical.setText(formatPercent(metrics.getHistoricalVolatility()));
        metricVaRHistorical.setText(formatPercent(metrics.getHistoricalVaR()));

        metricSharpeUnchanged.setText(formatNumber(metrics.getUnchangedSharpe()));
        metricVolatilityUnchanged.setText(formatPercent(metrics.getUnchangedVolatility()));
        metricVaRUnchanged.setText(formatPercent(metrics.getUnchangedVaR()));

        metricSharpeChanged.setText(formatNumber(metrics.getRebalancedSharpe()));
        colorAfterLabel(metricSharpeChanged, metrics.getRebalancedSharpe(), metrics.getUnchangedSharpe(), true);

        metricVolatilityChanged.setText(formatPercent(metrics.getRebalancedVolatility()));
        colorAfterLabel(metricVolatilityChanged, metrics.getRebalancedVolatility(), metrics.getUnchangedVolatility(),
                        false);

        metricVaRChanged.setText(formatPercent(metrics.getRebalancedVaR()));
        colorAfterLabel(metricVaRChanged, metrics.getRebalancedVaR(), metrics.getUnchangedVaR(), false);

        Composite metricsGroup = metricSharpeHistorical.getParent();
        metricsGroup.setRedraw(false);
        metricsGroup.layout(true, true);
        metricsGroup.setRedraw(true);

        ordersViewer.setData("ctx", ctx); //$NON-NLS-1$
        ordersViewer.setInput(ctx.getProposedOrders() != null ? ctx.getProposedOrders() : new ArrayList<>());

        impactViewer.setData("ctx", ctx); //$NON-NLS-1$
        impactViewer.setInput(ctx.getAssets() != null ? ctx.getAssets() : new ArrayList<>());

        StringBuilder sb = new StringBuilder();
        if (ctx.getLogger() != null)
            for (DecisionLogger.LogEntry entry : ctx.getLogger().getLogs())
                sb.append("[").append(entry.getLayerName()).append("] ") //$NON-NLS-1$ //$NON-NLS-2$
                                .append(entry.getMessage()).append("\n"); //$NON-NLS-1$
        logText.setText(sb.length() > 0 ? sb.toString() : Messages.LabelRebalancerNoLogEntries);
    }

    private void clearCache()
    {
        cachedAssets = null;
        cachedPrices = null;
        cachedWeights = null;
        cachedTotalValue = 0.0;
        cachedTotalCash = 0.0;
        cachedInterval = null;
        portfolioIndex = null;
    }

    @Override
    public void reportingPeriodUpdated()
    {
        clearCache();
        runAnalysis();
    }
    
    @Override
    public void notifyModelUpdated()
    {
        clearCache();
    }

    private void setStatus(String text, Color background)
    {
        statusLabel.setText(text);

        Color foreground = (background == colorAmber) ? Display.getDefault().getSystemColor(SWT.COLOR_BLACK)
                        : Display.getDefault().getSystemColor(SWT.COLOR_WHITE);

        statusLabel.setData("current_bg", background); //$NON-NLS-1$
        statusLabel.setData("current_fg", foreground); //$NON-NLS-1$

        statusLabel.setBackground(background);
        statusLabel.setForeground(foreground);

        statusLabel.redraw();
        statusLabel.update();
        statusLabel.getParent().layout(true);
    }

    private void colorAfterLabel(Label label, double afterValue, double beforeValue, boolean higherIsBetter)
    {
        Color fg;
        if (Double.isNaN(afterValue) || Double.isNaN(beforeValue))
        {
            fg = Display.getDefault().getSystemColor(SWT.COLOR_WIDGET_FOREGROUND);
        }
        else
        {
            boolean improved = higherIsBetter ? (afterValue > beforeValue) : (afterValue < beforeValue);
            fg = improved ? colorGreen : colorRed;
        }

        label.setData("metric_fg", fg); //$NON-NLS-1$
        label.setForeground(fg);
    }

    private void loadPreferences()
    {
        name.abuchen.portfolio.model.Client client = getClient();

        int stratIdx = parseInt(client.getProperty(PREF_STRATEGY), 0);
        strategyCombo.select(stratIdx >= 0 && stratIdx < 2 ? stratIdx : 0);

        maxWeightSpinner.setSelection(parseInt(client.getProperty(PREF_MAX_WEIGHT), 50));
        minWeightSpinner.setSelection(parseInt(client.getProperty(PREF_MIN_WEIGHT), 0));
        correlationSpinner.setSelection(parseInt(client.getProperty(PREF_CORRELATION), 85));
        inertiaSpinner.setSelection(parseInt(client.getProperty(PREF_INERTIA), 2));
        maxVolSpinner.setSelection(parseInt(client.getProperty(PREF_MAX_VOL), 100));
        monitoringSpinner.setSelection(parseInt(client.getProperty(PREF_MONITORING), 1440));

        allowFractionsCheck.setSelection(Boolean.parseBoolean(client.getProperty(PREF_ALLOW_FRACTIONS)));

        fixedFeeText.setText(nullOrBlank(client.getProperty(PREF_FIXED_FEE),    "0.00")); //$NON-NLS-1$
        double varFeePercent = parseDouble(client.getProperty(PREF_VAR_FEE), 0.0);
        varFeeText.setText(String.format(java.util.Locale.US, "%.2f", varFeePercent)); //$NON-NLS-1$
        cashBufferText.setText(nullOrBlank(client.getProperty(PREF_CASH_BUFFER),"0.00")); //$NON-NLS-1$
        newCapitalText.setText(nullOrBlank(client.getProperty(PREF_NEW_CAPITAL),"0.00")); //$NON-NLS-1$

        String frozen = client.getProperty(PREF_FROZEN);
        if (frozen != null && !frozen.isBlank())
            for (String s : frozen.split(",")) //$NON-NLS-1$
                if (!s.isBlank())
                    frozenList.add(s.trim());
    }

    private void savePreferences(RebalancingConfig config)
    {
        name.abuchen.portfolio.model.Client client = getClient();
        client.setProperty(PREF_STRATEGY, String.valueOf(config.getStrategy() == Strategy.MAX_SHARPE ? 0 : 1));
        client.setProperty(PREF_MAX_WEIGHT, String.valueOf((int) Math.round(config.getMaxWeightPerAsset() * 100)));
        client.setProperty(PREF_MIN_WEIGHT, String.valueOf((int) Math.round(config.getMinWeightPerAsset() * 100)));
        client.setProperty(PREF_CORRELATION, String.valueOf((int) Math.round(config.getCorrelationThreshold() * 100)));
        client.setProperty(PREF_INERTIA, String.valueOf((int) Math.round(config.getInertiaTolerance() * 100)));
        client.setProperty(PREF_MAX_VOL, String.valueOf((int) Math.round(config.getMaxPortfolioVolatility() * 100)));
        client.setProperty(PREF_MONITORING, String.valueOf(config.getMonitoringIntervalMinutes()));
        client.setProperty(PREF_ALLOW_FRACTIONS, String.valueOf(config.isAllowFractions()));
        client.setProperty(PREF_FIXED_FEE, String.valueOf(config.getCommissionFixed()));
        client.setProperty(PREF_VAR_FEE, String.valueOf(config.getCommissionVariable() * 100));
        client.setProperty(PREF_CASH_BUFFER, String.valueOf(config.getCashBuffer()));
        client.setProperty(PREF_NEW_CAPITAL, String.valueOf(config.getNewCashAmount()));
        client.setProperty(PREF_FROZEN, String.join(",", frozenList.getItems())); //$NON-NLS-1$
    }

    private Spinner createPercentSpinner(Composite parent, int defaultVal)
    {
        Spinner s = new Spinner(parent, SWT.BORDER);
        s.setMinimum(0);
        s.setMaximum(100);
        s.setSelection(defaultVal);
        GridDataFactory.fillDefaults().grab(true, false).applyTo(s);
        return s;
    }

    private Text createNumberText(Composite parent, String defaultVal)
    {
        Text t = new Text(parent, SWT.BORDER);
        t.setText(defaultVal);
        GridDataFactory.fillDefaults().grab(true, false).applyTo(t);
        return t;
    }

    private double parseDouble(String text, double fallback)
    {
        if (text == null || text.isBlank())
            return fallback;

        try
        {
            return Double.parseDouble(text.replace(',', '.'));
        }
        catch (NumberFormatException e)
        {
            return fallback;
        }
    }

    private int parseInt(String val, int fallback)
    {
        try
        {
            return val == null ? fallback : Integer.parseInt(val);
        }
        catch (Exception e)
        {
            return fallback;
        }
    }

    private String nullOrBlank(String value, String fallback)
    {
        return (value == null || value.isBlank()) ? fallback : value;
    }

    private String formatNumber(double value)
    {
        return Double.isNaN(value) ? "-" : String.format("%,.2f", value); //$NON-NLS-1$ //$NON-NLS-2$
    }

    private String formatPercent(double value)
    {
        return Double.isNaN(value) ? "-" : String.format("%,.2f%%", value * 100.0); //$NON-NLS-1$ //$NON-NLS-2$
    }

    private void resetToDefaults()
    {
        strategyCombo.select(0);
        monitoringSpinner.setSelection(1440);
        maxWeightSpinner.setSelection(50);
        minWeightSpinner.setSelection(0);
        correlationSpinner.setSelection(85);
        inertiaSpinner.setSelection(2);
        maxVolSpinner.setSelection(100);
        allowFractionsCheck.setSelection(false);
        fixedFeeText.setText("0.00"); //$NON-NLS-1$
        varFeeText.setText("0.00"); //$NON-NLS-1$
        cashBufferText.setText("0.00"); //$NON-NLS-1$
        newCapitalText.setText("0.00"); //$NON-NLS-1$
        frozenList.removeAll();
    }
}