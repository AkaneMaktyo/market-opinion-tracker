package com.personal.tracker.repository.trading;

import com.personal.tracker.repository.JdbcSupport;
import com.personal.tracker.service.trading.binance.BinanceSpotClient.OrderSnapshot;
import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class SignalTradeRepository {
  private final JdbcTemplate jdbc;
  private final RowMapper<TradePlan> planMapper = (rs, rowNum) -> new TradePlan(
      rs.getString("id"), rs.getString("alert_id"), rs.getString("instrument_id"),
      rs.getString("asset_class"), rs.getString("provider"), rs.getString("exchange_symbol"),
      rs.getString("base_asset"), rs.getString("quote_asset"), rs.getString("side"),
      rs.getBigDecimal("total_cost"), rs.getInt("batch_count"), rs.getString("environment"),
      rs.getBoolean("paper"), rs.getString("status"), rs.getString("error_message"),
      rs.getString("created_at"), rs.getString("updated_at"));
  private final RowMapper<TradeOrder> orderMapper = (rs, rowNum) -> new TradeOrder(
      rs.getString("id"), rs.getString("plan_id"), rs.getInt("batch_no"),
      rs.getString("exchange_symbol"), rs.getString("side"), rs.getString("order_type"),
      rs.getBigDecimal("price"), rs.getBigDecimal("planned_cost"), rs.getBigDecimal("quantity"),
      rs.getString("client_order_id"), rs.getString("exchange_order_id"), rs.getString("status"),
      rs.getBigDecimal("executed_quantity"), rs.getBigDecimal("cumulative_quote"),
      rs.getBigDecimal("average_price"), rs.getString("error_message"),
      rs.getString("created_at"), rs.getString("updated_at"));
  private final RowMapper<OpenTradeOrder> openOrderMapper = (rs, rowNum) ->
      new OpenTradeOrder(rs.getString("provider"), orderMapper.mapRow(rs, rowNum));

  public SignalTradeRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @PostConstruct
  public void initialize() {
    jdbc.execute("""
        CREATE TABLE IF NOT EXISTS signal_trade_plans (
          id VARCHAR(64) PRIMARY KEY,
          alert_id VARCHAR(64) NOT NULL,
          instrument_id VARCHAR(64) NOT NULL,
          asset_class VARCHAR(16) NOT NULL,
          provider VARCHAR(32) NOT NULL,
          exchange_symbol VARCHAR(64) NOT NULL,
          base_asset VARCHAR(32) NOT NULL,
          quote_asset VARCHAR(32) NOT NULL,
          side VARCHAR(8) NOT NULL,
          total_cost DECIMAL(24, 8) NOT NULL,
          batch_count INT NOT NULL,
          environment VARCHAR(32) NOT NULL,
          paper BOOLEAN NOT NULL DEFAULT TRUE,
          status VARCHAR(32) NOT NULL,
          error_message TEXT,
          created_at VARCHAR(64) NOT NULL,
          updated_at VARCHAR(64) NOT NULL,
          UNIQUE KEY uq_signal_trade_alert(alert_id),
          INDEX idx_signal_trade_status(provider, status, updated_at)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
        """);
    jdbc.execute("""
        CREATE TABLE IF NOT EXISTS signal_trade_orders (
          id VARCHAR(64) PRIMARY KEY,
          plan_id VARCHAR(64) NOT NULL,
          batch_no INT NOT NULL,
          exchange_symbol VARCHAR(64) NOT NULL,
          side VARCHAR(8) NOT NULL,
          order_type VARCHAR(16) NOT NULL,
          price DECIMAL(24, 8) NOT NULL,
          planned_cost DECIMAL(24, 8) NOT NULL,
          quantity DECIMAL(32, 12) NOT NULL,
          client_order_id VARCHAR(64) NOT NULL,
          exchange_order_id VARCHAR(64),
          status VARCHAR(32) NOT NULL,
          executed_quantity DECIMAL(32, 12) NOT NULL DEFAULT 0,
          cumulative_quote DECIMAL(24, 8) NOT NULL DEFAULT 0,
          average_price DECIMAL(24, 8) NOT NULL DEFAULT 0,
          error_message TEXT,
          created_at VARCHAR(64) NOT NULL,
          updated_at VARCHAR(64) NOT NULL,
          UNIQUE KEY uq_signal_trade_batch(plan_id, batch_no),
          UNIQUE KEY uq_signal_trade_client_order(client_order_id),
          INDEX idx_signal_trade_order_status(status, updated_at)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
        """);
  }

  @Transactional
  public TradePlan create(TradePlan plan, List<NewOrder> orders) {
    jdbc.update("""
        INSERT INTO signal_trade_plans(
          id, alert_id, instrument_id, asset_class, provider, exchange_symbol,
          base_asset, quote_asset, side, total_cost, batch_count, environment,
          paper, status, error_message, created_at, updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, plan.id(), plan.alertId(), plan.instrumentId(), plan.assetClass(), plan.provider(),
        plan.exchangeSymbol(), plan.baseAsset(), plan.quoteAsset(), plan.side(), plan.totalCost(),
        plan.batchCount(), plan.environment(), plan.paper(), plan.status(), plan.errorMessage(),
        plan.createdAt(), plan.updatedAt());
    for (NewOrder order : orders) {
      jdbc.update("""
          INSERT INTO signal_trade_orders(
            id, plan_id, batch_no, exchange_symbol, side, order_type, price,
            planned_cost, quantity, client_order_id, status, created_at, updated_at
          ) VALUES (?, ?, ?, ?, ?, 'LIMIT', ?, ?, ?, ?, 'PLANNED', ?, ?)
          """, order.id(), plan.id(), order.batchNo(), plan.exchangeSymbol(), plan.side(),
          order.price(), order.plannedCost(), order.quantity(), order.clientOrderId(),
          plan.createdAt(), plan.updatedAt());
    }
    return plan;
  }

  public List<TradePlan> plans() {
    return jdbc.query("SELECT * FROM signal_trade_plans ORDER BY created_at DESC", planMapper);
  }

  public Optional<TradePlan> findByAlertId(String alertId) {
    return jdbc.query("SELECT * FROM signal_trade_plans WHERE alert_id = ?", planMapper, alertId)
        .stream().findFirst();
  }

  public Optional<TradePlan> findPlan(String planId) {
    return jdbc.query("SELECT * FROM signal_trade_plans WHERE id = ?", planMapper, planId)
        .stream().findFirst();
  }

  public List<TradeOrder> orders(String planId) {
    return jdbc.query("SELECT * FROM signal_trade_orders WHERE plan_id = ? ORDER BY batch_no",
        orderMapper, planId);
  }

  public List<OpenTradeOrder> openOrders() {
    return jdbc.query("""
        SELECT p.provider, o.* FROM signal_trade_orders o
        JOIN signal_trade_plans p ON p.id = o.plan_id
        WHERE p.provider IN ('BINANCE', 'BINANCE_STOCKS') AND p.paper = FALSE
          AND o.status IN ('SUBMITTING', 'NEW', 'PARTIALLY_FILLED', 'UNKNOWN')
        ORDER BY o.updated_at
        """, openOrderMapper);
  }

  public void markSubmitting(String orderId) {
    jdbc.update("UPDATE signal_trade_orders SET status = 'SUBMITTING', error_message = NULL, updated_at = ? WHERE id = ?",
        JdbcSupport.now(), orderId);
  }

  public void updateOrder(String orderId, OrderSnapshot order) {
    jdbc.update("""
        UPDATE signal_trade_orders
        SET exchange_order_id = ?, status = ?, quantity = ?, executed_quantity = ?,
            cumulative_quote = ?, average_price = ?, price = ?, error_message = NULL, updated_at = ?
        WHERE id = ?
        """, order.orderId(), order.status(), order.originalQuantity(), order.executedQuantity(),
        order.cumulativeQuote(), order.averagePrice(), order.price(), JdbcSupport.now(), orderId);
  }

  public void markOrderError(String orderId, String status, String error) {
    jdbc.update("UPDATE signal_trade_orders SET status = ?, error_message = ?, updated_at = ? WHERE id = ?",
        status, error, JdbcSupport.now(), orderId);
  }

  public void refreshPlanStatus(String planId) {
    List<String> statuses = jdbc.queryForList(
        "SELECT status FROM signal_trade_orders WHERE plan_id = ?", String.class, planId);
    if (statuses.isEmpty()) return;
    String status;
    if (statuses.stream().allMatch("FILLED"::equals)) {
      status = "FILLED";
    } else if (statuses.stream().anyMatch(item -> "ERROR".equals(item) || "REJECTED".equals(item)
        || "UNKNOWN".equals(item))) {
      status = "ERROR";
    } else if (statuses.stream().anyMatch(item -> "FILLED".equals(item) || "PARTIALLY_FILLED".equals(item))) {
      status = "PARTIALLY_FILLED";
    } else if (statuses.stream().anyMatch(item -> "NEW".equals(item) || "SUBMITTING".equals(item))) {
      status = "ACTIVE";
    } else {
      status = "PLANNED";
    }
    jdbc.update("UPDATE signal_trade_plans SET status = ?, updated_at = ? WHERE id = ?",
        status, JdbcSupport.now(), planId);
  }

  public List<PositionCost> positionCosts() {
    return jdbc.query("""
        SELECT p.provider, p.exchange_symbol, p.base_asset, p.quote_asset,
               SUM(o.executed_quantity) executed_quantity,
               SUM(o.cumulative_quote) cumulative_quote
        FROM signal_trade_orders o
        JOIN signal_trade_plans p ON p.id = o.plan_id
        WHERE p.provider IN ('BINANCE', 'BINANCE_STOCKS')
          AND p.side = 'BUY' AND p.paper = FALSE
          AND o.executed_quantity > 0
        GROUP BY p.provider, p.exchange_symbol, p.base_asset, p.quote_asset
        """, (rs, rowNum) -> new PositionCost(
        rs.getString("provider"), rs.getString("exchange_symbol"),
        rs.getString("base_asset"), rs.getString("quote_asset"),
        rs.getBigDecimal("executed_quantity"), rs.getBigDecimal("cumulative_quote")));
  }

  public record TradePlan(
      String id, String alertId, String instrumentId, String assetClass, String provider,
      String exchangeSymbol, String baseAsset, String quoteAsset, String side,
      BigDecimal totalCost, int batchCount, String environment, boolean paper,
      String status, String errorMessage, String createdAt, String updatedAt) {
  }

  public record NewOrder(
      String id, int batchNo, BigDecimal price, BigDecimal plannedCost,
      BigDecimal quantity, String clientOrderId) {
  }

  public record TradeOrder(
      String id, String planId, int batchNo, String exchangeSymbol, String side,
      String orderType, BigDecimal price, BigDecimal plannedCost, BigDecimal quantity,
      String clientOrderId, String exchangeOrderId, String status,
      BigDecimal executedQuantity, BigDecimal cumulativeQuote, BigDecimal averagePrice,
      String errorMessage, String createdAt, String updatedAt) {
  }

  public record OpenTradeOrder(String provider, TradeOrder order) {
  }

  public record PositionCost(
      String provider, String exchangeSymbol, String baseAsset, String quoteAsset,
      BigDecimal executedQuantity, BigDecimal cumulativeQuote) {
  }
}
