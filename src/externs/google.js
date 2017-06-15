var google = {};

google.auth = {};

/**
 * @constructor
 */
google.auth.OAuth2 = function() {};
google.auth.OAuth2.prototype.setCredentials = function() {};
google.auth.OAuth2.prototype.generateAuthUrl = function(opts) {};
google.auth.OAuth2.prototype.getToken = function(opts, callback) {};

google.calendar.Calendar;
google.calendar.Calendar.events = {};
google.calendar.Calendar.events.prototype.list = function(opts, callback) {};
google.calendar.Calendar.calendars = {};
google.calendar.Calendar.calendars.prototype.get = function(opts, callback) {};
/**
* @returns {google.calendar.Calendar}
*/
google.calendar = function(opts) {};
