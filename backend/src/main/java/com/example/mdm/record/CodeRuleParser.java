package com.example.mdm.record;

import com.example.mdm.common.error.BusinessException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class CodeRuleParser {
  private static final Pattern TOKEN = Pattern.compile("\\{([^}]*)}");
  private static final Pattern SEQUENCE = Pattern.compile("0*1");

  public CodeRule parse(String pattern) {
    if (pattern == null || pattern.isBlank() || pattern.length() > 255) {
      throw BusinessException.badRequest("Invalid code rule pattern");
    }
    Matcher matcher = TOKEN.matcher(pattern);
    int sequenceWidth = 0;
    boolean dateSeen = false;
    StringBuffer literal = new StringBuffer();
    while (matcher.find()) {
      String token = matcher.group(1);
      if ("yyyyMMdd".equals(token)) {
        if (dateSeen) throw BusinessException.badRequest("Code rule has multiple date variables");
        dateSeen = true;
      } else if (SEQUENCE.matcher(token).matches()) {
        if (sequenceWidth != 0) throw BusinessException.badRequest("Code rule has multiple sequences");
        sequenceWidth = token.length();
      } else {
        throw BusinessException.badRequest("Unknown code rule variable: " + token);
      }
      matcher.appendReplacement(literal, "");
    }
    matcher.appendTail(literal);
    if (literal.indexOf("{") >= 0 || literal.indexOf("}") >= 0 || sequenceWidth == 0) {
      throw BusinessException.badRequest("Invalid code rule pattern");
    }
    return new CodeRule(0, pattern, sequenceWidth);
  }
}
