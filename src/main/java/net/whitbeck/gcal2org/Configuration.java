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

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

final class Configuration {

  private static final String PROPERTY_PREFIX = "org-property.";
  private static final String CALENDARS_PREFIX = "calendars.";

  private final String title;
  private final String fileTags;
  private final String tags;
  private final String appointmentCategory;
  private final String allDayCategory;
  private final String statusKey;
  private final String creatorKey;
  private final String hangoutLinkKey;
  private final String htmlLinkKey;
  private final String creationTimeKey;
  private final String attendeesKey;
  private final List<CalendarConfiguration> calendarConfigs;

  private Configuration(String title, String fileTags, String tags, String appointmentCategory,
                        String allDayCategory, String statusKey, String creatorKey,
                        String hangoutLinkKey, String htmlLinkKey, String creationTimeKey,
                        String attendeesKey, List<CalendarConfiguration> calendarConfigs) {
    this.title = title;
    this.fileTags = fileTags;
    this.tags = tags;
    this.appointmentCategory = appointmentCategory;
    this.allDayCategory = allDayCategory;
    this.statusKey = statusKey;
    this.creatorKey = creatorKey;
    this.hangoutLinkKey = hangoutLinkKey;
    this.htmlLinkKey = htmlLinkKey;
    this.creationTimeKey = creationTimeKey;
    this.attendeesKey = attendeesKey;
    this.calendarConfigs = calendarConfigs;
  }

  String getTitle() {
    return title;
  }

  String getFileTags() {
    return fileTags;
  }

  String getTags() {
    return tags;
  }

  String getAppointmentCategory() {
    return appointmentCategory;
  }

  String getAllDayCategory() {
    return allDayCategory;
  }

  String getStatusKey() {
    return statusKey;
  }

  String getCreatorKey() {
    return creatorKey;
  }

  String getHangoutLinkKey() {
    return hangoutLinkKey;
  }

  String getHtmlLinkKey() {
    return htmlLinkKey;
  }

  String getCreationTimeKey() {
    return creationTimeKey;
  }

  String getAttendeesKey() {
    return attendeesKey;
  }

  List<CalendarConfiguration> getCalendarConfigurations() {
    return calendarConfigs;
  }

  private static Builder setOrgPropertyKey(Builder builder, String name, String value) {
    if (name.equals("status")) {
      return builder.setStatusKey(value);
    } else if (name.equals("creator")) {
      return builder.setCreatorKey(value);
    } else if (name.equals("hangoutlink")) {
      return builder.setHangoutLinkKey(value);
    } else if (name.equals("htmllink")) {
      return builder.setHtmlLinkKey(value);
    } else if (name.equals("creationtime")) {
      return builder.setCreationTimeKey(value);
    } else if (name.equals("attendees")) {
      return builder.setAttendeesKey(value);
    } else {
      throw new IllegalStateException(String.format("Unknown option %s%s.", PROPERTY_PREFIX, name));
    }
  }

  private static Builder setCalendarProperty(Builder builder, String name, String value) {
    String[] elems = name.split("\\.", 2);
    if (elems.length != 2) {
      throw new IllegalStateException(
          String.format("Unknown option %s%s.", CALENDARS_PREFIX, name));
    }
    String calendarId = elems[0];
    String prop = elems[1];
    if (prop.equals("id")) {
      return builder.setCalendarId(calendarId, value);
    } else if (prop.equals("tags")) {
      return builder.setCalendarTags(calendarId, value);
    } else if (prop.equals("start")) {
      return builder.setCalendarStartDate(calendarId, value);
    } else if (prop.equals("end")) {
      return builder.setCalendarEndDate(calendarId, value);
    } else {
      throw new IllegalStateException(
          String.format("Unknown option %s for calendar %s.", prop, calendarId));
    }
  }

  private static Builder setProperty(Builder builder, String name, String value) {
    if (name.equals("title")) {
      return builder.setTitle(value);
    } else if (name.equals("filetags")) {
      return builder.setFileTags(value);
    } else if (name.equals("tags")) {
      return builder.setTags(value);
    } else if (name.equals("appointmentcategory")) {
      return builder.setAppointmentCategory(value);
    } else if (name.equals("alldaycategory")) {
      return builder.setAllDayCategory(value);
    } else if (name.startsWith(PROPERTY_PREFIX)) {
      return setOrgPropertyKey(builder,
                               name.substring(PROPERTY_PREFIX.length(), name.length()),
                               value);
    } else if (name.startsWith(CALENDARS_PREFIX)) {
      return setCalendarProperty(builder,
                                 name.substring(CALENDARS_PREFIX.length(), name.length()),
                                 value);
    } else {
      throw new IllegalStateException(String.format("Unknown option %s.", name));
    }
  }

  static Configuration load(File file) throws IOException {
    Builder builder = new Builder();
    Properties props = new Properties();
    props.load(new FileReader(file));
    for (Map.Entry<Object, Object> entry : props.entrySet()) {
      setProperty(builder,
                  ((String)entry.getKey()).toLowerCase(),
                  (String)entry.getValue());
    }
    return builder.build();
  }

  private static final class Builder {

    private String title;
    private String fileTags;
    private String tags;
    private String appointmentCategory = "Appt";
    private String allDayCategory = "Event";
    private String statusKey = "status";
    private String creatorKey = "creator";
    private String hangoutLinkKey = "hangout-link";
    private String htmlLinkKey = "html-link";
    private String creationTimeKey = "created-at";
    private String attendeesKey = "attendees";
    private final Date now = new Date();

    private Map<String, CalendarConfiguration.Builder> calendarConfigMap =
        new HashMap<String, CalendarConfiguration.Builder>();

    private Builder setTitle(String value) {
      title = value;
      return this;
    }

    private Builder setFileTags(String value) {
      fileTags = value;
      return this;
    }

    private Builder setTags(String value) {
      tags = value;
      return this;
    }

    private Builder setAppointmentCategory(String value) {
      appointmentCategory = value;
      return this;
    }

    private Builder setAllDayCategory(String value) {
      allDayCategory = value;
      return this;
    }

    private Builder setStatusKey(String value) {
      statusKey = value;
      return this;
    }

    private Builder setCreatorKey(String value) {
      creatorKey = value;
      return this;
    }

    private Builder setHangoutLinkKey(String value) {
      hangoutLinkKey = value;
      return this;
    }

    private Builder setHtmlLinkKey(String value) {
      htmlLinkKey = value;
      return this;
    }

    private Builder setCreationTimeKey(String value) {
      creationTimeKey = value;
      return this;
    }

    private Builder setAttendeesKey(String value) {
      attendeesKey = value;
      return this;
    }

    private CalendarConfiguration.Builder getOrCreateCalendarBuilder(String calendarId) {
      CalendarConfiguration.Builder builder = calendarConfigMap.get(calendarId);
      if (builder == null) {
        builder = new CalendarConfiguration.Builder(now, calendarId);
        calendarConfigMap.put(calendarId, builder);
      }
      return builder;
    }

    private Builder setCalendarId(String calendarId, String value) {
      getOrCreateCalendarBuilder(calendarId).setId(value);
      return this;
    }

    private Builder setCalendarTags(String calendarId, String value) {
      getOrCreateCalendarBuilder(calendarId).setTags(value);
      return this;
    }

    private Builder setCalendarStartDate(String calendarId, String value) {
      getOrCreateCalendarBuilder(calendarId).setStartDate(value);
      return this;
    }

    private Builder setCalendarEndDate(String calendarId, String value) {
      getOrCreateCalendarBuilder(calendarId).setEndDate(value);
      return this;
    }

    private Configuration build() {
      List<CalendarConfiguration> calendarConfigs =
          new ArrayList<CalendarConfiguration>(calendarConfigMap.size());
      for (CalendarConfiguration.Builder builder : calendarConfigMap.values()) {
        calendarConfigs.add(builder.build());
      }
      return new Configuration(title, fileTags, tags, appointmentCategory, allDayCategory,
                               statusKey, creatorKey, hangoutLinkKey, htmlLinkKey, creationTimeKey,
                               attendeesKey, calendarConfigs);
    }

  }

}
