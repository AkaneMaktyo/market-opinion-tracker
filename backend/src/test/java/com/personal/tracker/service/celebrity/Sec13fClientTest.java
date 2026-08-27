package com.personal.tracker.service.celebrity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class Sec13fClientTest {

  @Test
  void keepsDirectDollarValuesWhenTheFilingUsesDollars() {
    String xml = informationTable("""
        <infoTable><nameOfIssuer>APPLE INC</nameOfIssuer><titleOfClass>COM</titleOfClass><cusip>037833100</cusip><value>65950296923</value><shrsOrPrnAmt><sshPrnamt>227917808</sshPrnamt></shrsOrPrnAmt></infoTable>
        <infoTable><nameOfIssuer>AMERICAN EXPRESS CO</nameOfIssuer><titleOfClass>COM</titleOfClass><cusip>025816109</cusip><value>51282319275</value><shrsOrPrnAmt><sshPrnamt>151610700</sshPrnamt></shrsOrPrnAmt></infoTable>
        """);

    var holdings = Sec13fClient.parseInformationTable(xml.getBytes(StandardCharsets.UTF_8));

    assertThat(holdings).extracting(Sec13fClient.SecHolding::reportedValue)
        .containsExactlyInAnyOrder(new java.math.BigDecimal("65950296923"), new java.math.BigDecimal("51282319275"));
  }

  @Test
  void rejectsAmbiguousValueUnitsBeforeAnySnapshotCanBeReplaced() {
    String xml = informationTable("""
        <infoTable><nameOfIssuer>EXAMPLE ONE</nameOfIssuer><titleOfClass>COM</titleOfClass><cusip>000000001</cusip><value>1200</value><shrsOrPrnAmt><sshPrnamt>100</sshPrnamt></shrsOrPrnAmt></infoTable>
        <infoTable><nameOfIssuer>EXAMPLE TWO</nameOfIssuer><titleOfClass>COM</titleOfClass><cusip>000000002</cusip><value>1500</value><shrsOrPrnAmt><sshPrnamt>100</sshPrnamt></shrsOrPrnAmt></infoTable>
        """);

    assertThatThrownBy(() -> Sec13fClient.parseInformationTable(xml.getBytes(StandardCharsets.UTF_8)))
        .hasMessageContaining("金额单位无法安全判定");
  }

  @Test
  void combinesDuplicateRowsUsingThePortfolioHoldingKey() {
    String xml = informationTable("""
          <infoTable>
            <nameOfIssuer>LIBERTY MEDIA CORP</nameOfIssuer>
            <titleOfClass>COM</titleOfClass>
            <cusip>92343E102</cusip>
            <value>100</value>
            <shrsOrPrnAmt><sshPrnamt>100</sshPrnamt></shrsOrPrnAmt>
          </infoTable>
          <infoTable>
            <nameOfIssuer>LIBERTY MEDIA CORP</nameOfIssuer>
            <titleOfClass>COM</titleOfClass>
            <cusip>92343E102</cusip>
            <value>250</value>
            <shrsOrPrnAmt><sshPrnamt>200</sshPrnamt></shrsOrPrnAmt>
          </infoTable>
          <infoTable>
            <nameOfIssuer>LIBERTY MEDIA CORP</nameOfIssuer>
            <titleOfClass>COM</titleOfClass>
            <cusip>92343E102</cusip>
            <value>60</value>
            <putCall>Call</putCall>
            <shrsOrPrnAmt><sshPrnamt>3</sshPrnamt></shrsOrPrnAmt>
          </infoTable>
        """);

    var holdings = Sec13fClient.parseInformationTable(xml.getBytes(StandardCharsets.UTF_8));

    assertThat(holdings).hasSize(2);
    assertThat(holdings).filteredOn(item -> item.putCall().isBlank()).singleElement().satisfies(item -> {
      assertThat(item.holdingKey()).isEqualTo("92343E102|COM|");
      assertThat(item.shares()).isEqualByComparingTo("300");
      assertThat(item.reportedValue()).isEqualByComparingTo("350000");
      assertThat(item.reportedUnitValue()).isEqualByComparingTo("1166.66666667");
    });
    assertThat(holdings).filteredOn(item -> "CALL".equals(item.putCall())).singleElement().satisfies(item -> {
      assertThat(item.shares()).isEqualByComparingTo("3");
      assertThat(item.reportedValue()).isEqualByComparingTo("60000");
    });
  }

  private static String informationTable(String rows) {
    return """
        <?xml version="1.0" encoding="UTF-8"?>
        <informationTable xmlns="http://www.sec.gov/edgar/document/thirteenf/informationtable">
        """ + rows + "</informationTable>";
  }
}
