# Export google calendar to org-mode

## Overview

A simple little CLI tool to export your google calendar into org-mode.

Unlike the [synchronization workflow](http://orgmode.org/worg/org-tutorials/org-google-sync.html) described on
the on the [Worg](http://orgmode.org/worg/) website, gcal-to-org uses Google's
[API Client Library for Java](https://developers.google.com/api-client-library/java/). Since it authenticates
using OAUTH, there is no need to make your calendar publicly available through a "hidden" url.

This tool is in the early stages and narrowly focused on my particular needs. However, it should be easy to
adapt it to your use-case, and I would be happy to iterate on the design.


## Installation

```bash
npm install -g gcal-to-org
```

## Usage

First create a `config.edn` config file somwhere. Below is a minimal example for a single
calendar. See [config.example.edn](config.example.edn) for a fully commented example.

```clojure
{:calendars [{:id "bob@gmail.com"}]
 ;; Google API credentials (see below)
 :client-id "123456789-abcdedfhijklmopqrst0123.apps.googleusercontent.com"
 :client-secret "qDcAHZyjdEDikPtIaaElOyF9"}
```

Then run just run the CLI tool to print org-mode text to stdout.

```bash
gcal-to-org path/to/config.edn
```

On the first run, it will open an OAUTH window in your default browser and cache your OAUTH tokens to
disk. On subsequent runs, it simply uses the cached credentials and behaves like any other CLI tool.


## Obtaining Google API credentials

You need to register gcal-to-org as an application yourself to obtain a _Client ID_ and _Client secret_.

Go to the Google API Manager and create a new project under any name.

Within that project, enable the "Calendar" API. There should be a searchbox where you can just enter those
terms.

In the sidebar, select "Credentials" and then create a new "OAuth Client ID". The application type is "Other".

You’ll be prompted to create a OAuth consent screen first. Fill out that form however you like.

Finally you should have a _Client ID_ and a _Client secret_. Provide these in the config below.


## Emacs integration

Everyone's setup is different, but in case you are interested, here is how I call the tool from emacs.

```emacs-lisp
;;; Write work agenda here
(defvar my-work-agenda-file "~/var/work.org")

;;; Add work agenda to default agenda files
(add-to-list 'org-agenda-files my-work-agenda-file)

(defun my-update-work-agenda-file ()
  "Create work agenda file by pulling from Google Calendar."
  (if (zerop (call-process "gcal-to-org" nil `(:file ,my-work-agenda-file) nil "/path/to/config.edn"))
      (let ((buf (find-buffer-visiting my-work-agenda-file)))
        ;; Revert work buffer if it exists
        (when buf
          (with-current-buffer buf
            (revert-buffer nil t)))
        (message "Successfully updated %s" my-work-agenda-file))
    (message "Failed to update %s" my-work-agenda-file)))

;;; Add command to call gcal-to-org from the agenda dispatch view. Bound to "w" key.
(add-to-list 'org-agenda-custom-commands
             '("w" "Update work agenda file"
               ;; This functions gets called with a match object that we discard.
               (lambda (_) (my-update-work-agenda-file)))
             t)
```
