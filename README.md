# Export google calendar to org-mode

## Overview

A simple little CLI tool to export your google calendar into org-mode.

Unlike the [synchronization workflow](http://orgmode.org/worg/org-tutorials/org-google-sync.html) described on
the on the [Worg](http://orgmode.org/worg/) website, gcal-to-org uses Google's
[API Client Library for Java](https://developers.google.com/api-client-library/java/). Since it authenticates
using OAUTH, there is no need to make your calendar publicly available through a "hidden" url.

This tool is in the early stages and narrowly focused on my particular needs. However, it should be easy to
adapt it to your use-case, and I would be happy to iterate on the design.

## Usage

First create the `$HOME/.gcal2org.properties` config file. Below is a minimal example for a single
calendar. See [examples/gcal2org.properties](examples/gcal2org.properties) for a full commented example.

```ini
# Tag all the entries with the :work: tag
filetags = work

# Settings for export the "sean" calendar
# The id of the calendar to export
calendars.sean.id = sean@foo.io
# Only import the events between 10 days ago and 30 days in the future
calendars.sean.start = -10
calendars.sean.end = 30
```

Then run just run the CLI tool to print org-mode text to stdout. Assuming you've downloaded the
[latest gcal-to-org build](https://s3.amazonaws.com/gcal2org.whitbeck.net/gcal-to-org-0.0.1-SNAPSHOT.jar),
it's as simple as:

```bash
$ java -jar gcal-to-org-0.0.1-SNAPSHOT.jar
```

On the first run, it will open an OAUTH window in your default browser. Thereafter, it caches the credentials
and behaves like any other CLI tool.

## Emacs integration

Everyone's setup is different, but in case you are interested, here is how I call the tool from emacs.

```emacs-lisp
;;; Write work agenda here
(defvar my-work-agenda-file "~/var/work.org")

;;; Add work agenda to default agenda files
(add-to-list 'org-agenda-files my-work-agenda-file)

(defun my-update-work-agenda-file ()
  "Create work agenda file by pulling from Google Calendar."
  (if (eq (call-process "java" nil `(:file ,my-work-agenda-file) nil
                        "-jar" "/home/sean/bin/gcal-to-org-0.0.1-SNAPSHOT.jar") 0)
      (progn
        ;; Revert work buffer if it exists
        (let ((buf (find-buffer-visiting my-work-agenda-file)))
          (when buf
            (with-current-buffer buf
                (revert-buffer nil t))))
        (message "Successfully updated %s" my-work-agenda-file))
    (message "Failed to update %s" my-work-agenda-file)))

;;; Add command to call gcal-to-org from the agenda dispatch view. Bound to "w" key.
(add-to-list 'org-agenda-custom-commands
             '("w" "Update work agenda file"
               ;; This functions gets called with a match object that we discard.
               (lambda (_) (my-update-work-agenda-file)))
             t)
```
