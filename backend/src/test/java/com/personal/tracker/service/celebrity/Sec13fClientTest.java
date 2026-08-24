package com.personal.tracker.service.celebrity;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class Sec13fClientTest {

  @Test
  void combinesDuplicateRowsUsingThePortfolioHoldingKey() {
    String xml = """
        <?xml version="1.0" encoding="UTF-8"?>
        <informationTable xmlns="http://www.sec.gov/edgar/document/thirteenf/informationtable">
          <infoTable>
            <nameOfIssuer>LIBERTY MEDIA CORP</nameOfIssuer>
            <titleOfClass>COM</titleOfClass>
            <cusip>92343E102</cusip>
            <value>100</value>
            <shrsOrPrnAmt><sshPrnamt>10</sshPrnamt></shrsOrPrnAmt>
          </infoTable>
          <infoTable>
            <nameOfIssuer>LIBERTY MEDIA CORP</nameOfIssuer>
            <titleOfClass>COM</titleOfClass>
            <cusip>92343E102</cusip>
            <value>250</value>
            <shrsOrPrnAmt><sshPrnamt>20</sshPrnamt></shrsOrPrnAmt>
          </infoTable>
          <infoTable>
            <nameOfIssuer>LIBERTY MEDIA CORP</nameOfIssuer>
            <titleOfClass>COM</titleOfClass>
            <cusip>92343E102</cusip>
            <value>60</value>
            <putCall>Call</putCall>
            <shrsOrPrnAmt><sshPrnamt>3</sshPrnamt></shrsOrPrnAmt>
          </infoTable>
        </informationTable>
        """;

    var holdings = Sec13fClient.parseInformationTable(xml.getBytes(StandardCharsets.UTF_8));

    assertThat(holdings).hasSize(2);
    assertThat(holdings).filteredOn(item -> item.putCall().isBlank()).singleElement().satisfies(item -> {
      assertThat(item.holdingKey()).isEqualTo("92343E102|COM|");
      assertThat(item.shares()).isEqualByComparingTo("30");
      assertThat(item.reportedValue()).isEqualByComparingTo("350000");
      assertThat(item.reportedUnitValue()).isEqualByComparingTo("11666.66666667");
    });
    assertThat(holdings).filteredOn(item -> "CALL".equals(item.putCall())).singleElement().satisfies(item -> {
      assertThat(item.shares()).isEqualByComparingTo("3");
      assertThat(item.reportedValue()).isEqualByComparingTo("60000");
    });
  }
}
