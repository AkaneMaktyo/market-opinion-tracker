package com.personal.tracker.service.trading.futures;

import com.fasterxml.jackson.databind.JsonNode;
import com.personal.tracker.service.trading.BitgetDemoClient;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/** 把 Bitget USDT 合约的原始持仓与账户数据整理为移动端可直接展示的结构。 */
@Service
public class BitgetFuturesPositionService {
  private static final int SCALE = 8;

  private final BitgetDemoClient bitget;

  public BitgetFuturesPositionService(BitgetDemoClient bitget) {
    this.bitget = bitget;
  }

  public FuturesPortfolio portfolio() {
    BitgetDemoClient.TradingStatus status = bitget.status();
    try {
      BitgetDemoClient.BitgetResponse positionResponse = bitget.positions();
      List<FuturesPosition> positions = parsePositions(positionResponse.data());
      FuturesAccount account = parseAccount(bitget.accounts().data(), status.marginCoin());
      return buildPortfolio(status, positions, account, null);
    } catch (RuntimeException error) {
      return buildPortfolio(status, List.of(), null,
          error.getMessage() == null ? "读取合约持仓失败" : error.getMessage());
    }
  }

  private FuturesPortfolio buildPortfolio(
      BitgetDemoClient.TradingStatus status,
      List<FuturesPosition> positions,
      FuturesAccount account,
      String errorMessage) {
    BigDecimal totalMargin = BigDecimal.ZERO;
    BigDecimal totalUnrealized = BigDecimal.ZERO;
    for (FuturesPosition position : positions) {
      totalMargin = totalMargin.add(nvl(position.margin()));
      totalUnrealized = totalUnrealized.add(nvl(position.unrealizedPL()));
    }
    BigDecimal totalReturn = ratio(totalUnrealized, totalMargin);
    String message = errorMessage != null ? errorMessage
        : (status.demo() ? "Bitget USDT 合约 · 模拟盘" : "Bitget USDT 合约 · 实盘");
    return new FuturesPortfolio(
        errorMessage == null,
        status.demo(),
        status.productType(),
        status.marginCoin(),
        message,
        account == null ? null : account.accountEquity(),
        account == null ? null : account.available(),
        positions.size(),
        totalMargin,
        totalUnrealized,
        totalReturn,
        Instant.now().toString(),
        positions);
  }

  private List<FuturesPosition> parsePositions(JsonNode data) {
    List<FuturesPosition> result = new ArrayList<>();
    if (data == null || !data.isArray()) {
      return result;
    }
    for (JsonNode node : data) {
      BigDecimal margin = firstDecimal(node, "marginSize", "occupyMargin", "isolatedMargin");
      BigDecimal unrealized = decimal(node, "unrealizedPL");
      result.add(new FuturesPosition(
          text(node, "symbol"),
          text(node, "marginCoin"),
          text(node, "holdSide"),
          node.path("isolated").asBoolean(false),
          decimal(node, "leverage"),
          decimal(node, "total"),
          decimal(node, "openPriceAvg"),
          decimal(node, "markPrice"),
          decimal(node, "liquidationPrice"),
          margin,
          unrealized,
          ratio(unrealized, margin)));
    }
    return result;
  }

  private FuturesAccount parseAccount(JsonNode data, String marginCoin) {
    if (data == null || !data.isArray()) {
      return null;
    }
    for (JsonNode node : data) {
      if (!marginCoin.equalsIgnoreCase(text(node, "marginCoin"))) {
        continue;
      }
      return new FuturesAccount(decimal(node, "accountEquity"), decimal(node, "available"));
    }
    return null;
  }

  private static String text(JsonNode node, String field) {
    String value = node.path(field).asText("");
    return value == null ? "" : value.trim();
  }

  private static BigDecimal decimal(JsonNode node, String field) {
    String raw = text(node, field);
    if (raw.isEmpty()) {
      return null;
    }
    try {
      return new BigDecimal(raw).setScale(SCALE, RoundingMode.HALF_UP).stripTrailingZeros();
    } catch (NumberFormatException error) {
      return null;
    }
  }

  private static BigDecimal firstDecimal(JsonNode node, String... fields) {
    for (String field : fields) {
      BigDecimal value = decimal(node, field);
      if (value != null) {
        return value;
      }
    }
    return null;
  }

  private static BigDecimal ratio(BigDecimal pnl, BigDecimal margin) {
    if (pnl == null || margin == null || margin.signum() <= 0) {
      return null;
    }
    return pnl.multiply(BigDecimal.valueOf(100))
        .divide(margin, 2, RoundingMode.HALF_UP);
  }

  private static BigDecimal nvl(BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value;
  }

  private record FuturesAccount(BigDecimal accountEquity, BigDecimal available) {
  }

  public record FuturesPosition(
      String symbol,
      String marginCoin,
      String side,
      boolean isolated,
      BigDecimal leverage,
      BigDecimal size,
      BigDecimal openPriceAvg,
      BigDecimal markPrice,
      BigDecimal liquidationPrice,
      BigDecimal margin,
      BigDecimal unrealizedPL,
      BigDecimal returnRate) {
  }

  public record FuturesPortfolio(
      boolean accountReady,
      boolean demo,
      String productType,
      String marginCoin,
      String message,
      BigDecimal accountEquity,
      BigDecimal available,
      int positionCount,
      BigDecimal totalMargin,
      BigDecimal totalUnrealizedPL,
      BigDecimal totalReturnRate,
      String updatedAt,
      List<FuturesPosition> positions) {
  }
}
