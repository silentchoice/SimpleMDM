package com.example.mdm.record;

import java.time.LocalDate;

public record CodeRule(long masterTypeId, String pattern, int sequenceWidth) {
  public String render(LocalDate date, long sequence) {
    String padded = String.format("%0" + sequenceWidth + "d", sequence);
    return pattern.replace("{yyyyMMdd}", date.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE))
        .replaceAll("\\{0*1\\}", padded);
  }
}
