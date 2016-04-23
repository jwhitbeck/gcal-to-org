/**
 * Copyright (c) 2016 John Whitbeck. All rights reserved.
 *
 * <p>The use and distribution terms for this software are covered by the
 * Apache License 2.0 (https://www.apache.org/licenses/LICENSE-2.0.txt)
 * which can be found in the file al-v20.txt at the root of this distribution.
 * By using this software in any fashion, you are agreeing to be bound by
 * the terms of this license.
 *
 * <p>You must not remove this notice, or any other, from this software.
 */

package net.whitbeck.gcal2org;

import java.util.Calendar;
import java.util.Date;

final class CalendarConfiguration {

  private final String name;
  private final String id;
  private final String tags;
  private final Date start;
  private final Date end;

  private CalendarConfiguration(String name, String id, String tags, Date start, Date end) {
    this.name = name;
    this.id = id;
    this.tags = tags;
    this.start = start;
    this.end = end;
  }

  String getName() {
    return name;
  }

  String getId() {
    return id;
  }

  String getTags() {
    return tags;
  }

  Date getStartDate() {
    return start;
  }

  Date getEndDate() {
    return end;
  }

  static final class Builder {

    private final String name;
    private String id;
    private String tags;
    private Date start;
    private Date end;
    private final Date now;

    Builder(Date now, String name) {
      this.name = name;
      this.now = now;
      this.start = daysFromNow(-10);
      this.end = daysFromNow(30);
    }

    Builder setId(String value) {
      id = value;
      return this;
    }

    Builder setTags(String value) {
      tags = value;
      return this;
    }

    private Date daysFromNow(int numDays) {
      Calendar cal = Calendar.getInstance();
      cal.setTime(now);
      cal.add(Calendar.DATE, numDays);
      return cal.getTime();
    }

    Builder setStartDate(String value) {
      start = daysFromNow(Integer.parseInt(value));
      return this;
    }

    Builder setEndDate(String value) {
      end = daysFromNow(Integer.parseInt(value));
      return this;
    }

    CalendarConfiguration build() {
      if (start.after(end)) {
        throw new IllegalStateException(
          String.format("Start date (%s) cannot be later than end date (%s) for calendar %s.",
                        start, end, name));
      }
      if (id == null) {
        throw new IllegalStateException(
          String.format("Calendar %s is missing required 'id' parameter.", name));
      }
      return new CalendarConfiguration(name, id, tags, start, end);
    }
  }
}
