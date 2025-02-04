// Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import oracle.kubernetes.TestUtils;
import oracle.kubernetes.weblogic.domain.v2.Domain;
import oracle.kubernetes.weblogic.domain.v2.DomainCondition;
import oracle.kubernetes.weblogic.domain.v2.DomainConditionType;
import oracle.kubernetes.weblogic.domain.v2.DomainStatus;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeDiagnosingMatcher;

@SuppressWarnings("unused")
class DomainConditionMatcher extends TypeSafeDiagnosingMatcher<Domain> {
  private DomainConditionType expectedType;
  private String expectedStatus;
  private String expectedReason;
  private String expectedMessage;

  static DomainConditionMatcher hasCondition(DomainConditionType type) {
    return new DomainConditionMatcher(type);
  }

  private DomainConditionMatcher(DomainConditionType expectedType) {
    this.expectedType = expectedType;
  }

  DomainConditionMatcher withStatus(String status) {
    expectedStatus = status;
    return this;
  }

  DomainConditionMatcher withReason(String reason) {
    expectedReason = reason;
    return this;
  }

  DomainConditionMatcher withMessage(String message) {
    expectedMessage = message;
    return this;
  }

  @Override
  protected boolean matchesSafely(Domain item, Description mismatchDescription) {
    for (DomainCondition condition : getStatus(item).getConditions())
      if (matches(condition)) return true;

    mismatchDescription.appendValueList(
        "found domain with conditions ", ", ", ".", getStatus(item).getConditions());
    return false;
  }

  private boolean matches(DomainCondition condition) {
    if (expectedType != condition.getType()) return false;
    if (expectedStatus != null && !expectedStatus.equals(condition.getStatus())) return false;
    if (expectedMessage != null && !expectedMessage.equals(condition.getMessage())) return false;
    return expectedReason == null || expectedReason.equals(condition.getReason());
  }

  private DomainStatus getStatus(Domain domain) {
    return Optional.ofNullable(domain.getStatus()).orElse(new DomainStatus());
  }

  @Override
  public void describeTo(Description description) {
    List<String> expectations = new ArrayList<>();
    expectations.add(expectation("type", expectedType.toString()));
    if (expectedStatus != null) expectations.add(expectation("status", expectedStatus));
    if (expectedReason != null) expectations.add(expectation("reason", expectedReason));
    if (expectedMessage != null) expectations.add(expectation("reason", expectedMessage));
    description
        .appendText("domain containing condition: ")
        .appendText(TestUtils.joinListGrammatically(expectations));
  }

  private String expectation(String description, String value) {
    return description + " = '" + value + "'";
  }
}
