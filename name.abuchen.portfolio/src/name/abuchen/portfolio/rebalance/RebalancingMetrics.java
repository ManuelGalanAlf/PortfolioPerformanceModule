package name.abuchen.portfolio.rebalance;

/**
 * A data transfer object (DTO) that holds the calculated "Before" and "After"
 * values of Sharpe Ratio, Volatility, and Value at Risk (VaR).
 */
public class RebalancingMetrics
{
    private final double historicalSharpe;
    private final double unchangedSharpe;
    private final double rebalancedSharpe;
    private final double historicalVolatility;
    private final double unchangedVolatility;
    private final double rebalancedVolatility;
    private final double historicalVaR;
    private final double unchangedVaR;
    private final double rebalancedVaR;

    public RebalancingMetrics(
        double historicalSharpe,  double unchangedSharpe,  double rebalancedSharpe,
        double historicalVol,     double unchangedVol,     double rebalancedVol,
        double historicalVaR,     double unchangedVaR,     double rebalancedVaR)
    {
        this.historicalSharpe     = historicalSharpe;
        this.unchangedSharpe      = unchangedSharpe;
        this.rebalancedSharpe     = rebalancedSharpe;
        this.historicalVolatility = historicalVol;
        this.unchangedVolatility  = unchangedVol;
        this.rebalancedVolatility = rebalancedVol;
        this.historicalVaR        = historicalVaR;
        this.unchangedVaR         = unchangedVaR;
        this.rebalancedVaR        = rebalancedVaR;
    }

    public double getHistoricalSharpe()
    {
        return historicalSharpe;
    }

    public double getUnchangedSharpe()
    {
        return unchangedSharpe;
    }

    public double getRebalancedSharpe()
    {
        return rebalancedSharpe;
    }

    public double getHistoricalVolatility()
    {
        return historicalVolatility;
    }

    public double getUnchangedVolatility()
    {
        return unchangedVolatility;
    }

    public double getRebalancedVolatility()
    {
        return rebalancedVolatility;
    }

    public double getHistoricalVaR()
    {
        return historicalVaR;
    }

    public double getUnchangedVaR()
    {
        return unchangedVaR;
    }

    public double getRebalancedVaR()
    {
        return rebalancedVaR; 
    }
}
