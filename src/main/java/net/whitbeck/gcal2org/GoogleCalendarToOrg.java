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

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.client.util.store.DataStoreFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventAttendee;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.api.services.calendar.model.Events;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

public final class GoogleCalendarToOrg {

  /** Path to user configuration. */
  private static final File CONFIGURATION_FILE =
      new File(System.getProperty("user.home"), ".gcal2org.properties");


  /** The Google API application name. */
  private static final String APPLICATION_NAME = "gcal-to-org/0.0.1";


  /** Folder to store OAUTH 2.0 tokens for the Google API. */
  private static final File DATA_STORE_DIR =
      new File(System.getProperty("user.home"), ".cache/gcal2org");

  /**
   * Global instance of the {@link DataStoreFactory}. The best practice is to make it a single
   * globally shared instance across your application.
   */
  private static DataStoreFactory dataStoreFactory;

  /** Global instance of the HTTP transport. */
  private static HttpTransport httpTransport;

  /** Global instance of the JSON factory. */
  private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

  /** Global calendar client instance. */
  private static com.google.api.services.calendar.Calendar client;

  /** Org mode date-time formatter. */
  private static final DateFormat orgDateTimeFormatter =
      new SimpleDateFormat("yyyy-MM-dd EEE HH:mm");

  /** Org mode time formatter. */
  private static final DateFormat orgTimeFormatter =
      new SimpleDateFormat("HH:mm");

  /** Org mode date formatter. */
  private static final DateFormat orgDateFormatter =
      new SimpleDateFormat("yyyy-MM-dd EEE");

  /** Authorizes the installed application to access user's protected data. */
  private static Credential authorize() throws IOException {
    // load client secrets
    GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(
        JSON_FACTORY,
        new InputStreamReader(GoogleCalendarToOrg.class.getResourceAsStream("/client_id.json")));
    // set up authorization code flow
    GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
        httpTransport, JSON_FACTORY, clientSecrets,
        Collections.singleton(CalendarScopes.CALENDAR_READONLY))
        .setDataStoreFactory(dataStoreFactory)
        .build();
    // authorize
    return new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize("user");
  }

  // initialize all the static Google API fields that can throw exceptions.
  private static void initializeGoogleApi() throws GeneralSecurityException, IOException {
    // initialize the data store factory
    dataStoreFactory = new FileDataStoreFactory(DATA_STORE_DIR);
    // initialize the transport
    httpTransport = GoogleNetHttpTransport.newTrustedTransport();
    // authorization
    Credential credential = authorize();
    // set up global Calendar client instance
    client = new com.google.api.services.calendar.Calendar.Builder(
        httpTransport, JSON_FACTORY, credential).setApplicationName(APPLICATION_NAME).build();
  }

  private static Configuration readConfiguration() throws IOException {
    if (! CONFIGURATION_FILE.exists()) {
      throw new IllegalStateException(
          String.format("Configuration file %s does not exist.", CONFIGURATION_FILE.getPath()));
    }
    return Configuration.load(CONFIGURATION_FILE);
  }

  private static String getCurrentDateString() {
    return DateFormat.getDateTimeInstance().format(new Date());
  }

  private static void printHeader(Configuration conf) {
    if (conf.getTitle() != null) {
      System.out.println("#+TITLE: " + conf.getTitle());
    }
    if (conf.getFileTags() != null) {
      System.out.println("#+FILETAGS: " + conf.getFileTags());
    }
    if (conf.getTags() != null) {
      System.out.println("#+TAGS: " + conf.getTags());
    }
    System.out.println(
        String.format("#+COMMENT: Generated on %s by gcal-to-org. Do not edit manually.",
                      getCurrentDateString()));
    System.out.println();
  }

  private static void printOrgProperties(String... props) {
    System.out.println(":PROPERTIES:");
    for (int i = 0; i < props.length - 1; i += 2) {
      System.out.println(":" + props[i] + ": " + props[i + 1]);
    }
    System.out.println(":END:");
  }

  private static void printOrgCalendarHeader(CalendarConfiguration calendarConf)
    throws IOException {
    com.google.api.services.calendar.model.Calendar calendar
        = client.calendars().get(calendarConf.getId()).execute();
    if (calendarConf.getTags() != null) {
      System.out.println("* " + calendar.getSummary() + " " + calendarConf.getTags());
    } else {
      System.out.println("* " + calendar.getSummary());
    }
  }

  private static String formatOrgAllDayEvent(EventDateTime start, EventDateTime end) {
    // google calendar end dates are exclusive, whereas org mode end dates are inclusive...
    Calendar startCal = parseEventDate(start);
    Calendar endCal = parseEventDate(end);
    endCal.add(Calendar.DATE, -1);
    if (startCal.equals(endCal)) {
      return "<" + orgDateFormatter.format(startCal.getTime()) + ">";
    } else {
      return String.format("<%s>--<%s>",
                           orgDateFormatter.format(startCal.getTime()),
                           orgDateFormatter.format(endCal.getTime()));
    }
  }

  /* Hack to parse raw dates (e.g. 2016-01-01) that have no timezone info into the current
   * timezone */
  private static Calendar parseEventDate(EventDateTime eventDate) {
    Calendar cal = Calendar.getInstance();
    String[] elems = eventDate.getDate().toStringRfc3339().split("-", 3);
    cal.clear();
    cal.set(Integer.parseInt(elems[0]),
            Integer.parseInt(elems[1]) - 1,
            Integer.parseInt(elems[2]));
    return cal;
  }

  private static String formatOrgAppointment(EventDateTime start, EventDateTime end) {
    Calendar startCal = Calendar.getInstance();
    startCal.setTimeInMillis(start.getDateTime().getValue());
    Calendar endCal = Calendar.getInstance();
    endCal.setTimeInMillis(end.getDateTime().getValue());
    if (startCal.get(Calendar.DATE) == endCal.get(Calendar.DATE)) {
      return String.format("<%s-%s>",
                           orgDateTimeFormatter.format(startCal.getTime()),
                           orgTimeFormatter.format(endCal.getTime()));
    } else {
      return String.format("<%s>--<%s>",
                           orgDateTimeFormatter.format(startCal.getTime()),
                           orgDateTimeFormatter.format(endCal.getTime()));
    }
  }

  private static Category getCategory(Event event) {
    if (event.getStart().getDate() != null && event.getEnd().getDate() != null) {
      return Category.ALL_DAY;
    } else if (event.getStart().getDateTime() != null && event.getEnd().getDateTime() != null) {
      return Category.APPOINTMENT;
    } else {
      throw new IllegalStateException(
          "Event has both date and date-time bounds. Not currently supported.");
    }
  }

  private static String formatOrgTimestamp(Event event) {
    switch (getCategory(event)) {
      case ALL_DAY:
        return formatOrgAllDayEvent(event.getStart(), event.getEnd());
      case APPOINTMENT:
        return formatOrgAppointment(event.getStart(), event.getEnd());
      default:
        return null; // never reached
    }
  }

  private static String formatOrgUrl(String url, String description) {
    return String.format("[[%s][%s]]", url, description);
  }

  private static String formatAttendee(EventAttendee attendee) {
    return String.format("%s <%s>", attendee.getDisplayName(), attendee.getEmail());
  }

  private static String formatAttendees(List<EventAttendee> attendees) {
    if (attendees == null) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    Iterator<EventAttendee> it = attendees.iterator();
    while (it.hasNext()) {
      sb.append(formatAttendee(it.next()));
      if (it.hasNext()) {
        sb.append(", ");
      }
    }
    return sb.toString();
  }

  private static String formatCreator(Event.Creator creator) {
    return String.format("%s <%s>", creator.getDisplayName(), creator.getEmail());
  }

  private static String getCategoryString(Configuration conf, Event event) {
    switch (getCategory(event)) {
      case ALL_DAY:
        return conf.getAllDayCategory();
      case APPOINTMENT:
        return conf.getAppointmentCategory();
      default:
        return null; // never reached
    }
  }

  private static void printOrgCalendarEvent(Configuration conf, Event event) {
    System.out.println("** " + event.getSummary());
    System.out.println(formatOrgTimestamp(event));
    printOrgProperties("CATEGORY", getCategoryString(conf, event),
                       conf.getStatusKey(), event.getStatus(),
                       conf.getHangoutLinkKey(), formatOrgUrl(event.getHangoutLink(), "hangout"),
                       conf.getHtmlLinkKey(), formatOrgUrl(event.getHtmlLink(), "event"),
                       conf.getCreatorKey(), formatCreator(event.getCreator()),
                       conf.getCreationTimeKey(), event.getCreated().toString(),
                       conf.getAttendeesKey(), formatAttendees(event.getAttendees()));
    if (event.getDescription() != null) {
      System.out.println(event.getDescription());
    }
  }

  private static void printOrgCalendarEvents(Configuration conf,
                                             CalendarConfiguration calendarConf)
    throws IOException {
    Events events = client.events().list(calendarConf.getId())
        .setTimeMin(new DateTime(calendarConf.getStartDate()))
        .setTimeMax(new DateTime(calendarConf.getEndDate()))
        .setSingleEvents(true)
        .execute();
    for (Event event : events.getItems()) {
      printOrgCalendarEvent(conf, event);
    }
  }

  private static void printOrgCalendar(Configuration conf, CalendarConfiguration calendarConf)
    throws IOException {
    printOrgCalendarHeader(calendarConf);
    printOrgCalendarEvents(conf, calendarConf);

  }

  private static void printOrgMode(Configuration conf) throws IOException {
    printHeader(conf);
    for (CalendarConfiguration calendarConf : conf.getCalendarConfigurations()) {
      printOrgCalendar(conf, calendarConf);
    }
  }

  public static void main(String[] args) throws Exception {
    initializeGoogleApi();
    printOrgMode(readConfiguration());
  }

}
