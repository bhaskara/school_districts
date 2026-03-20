# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is an Android app for finding the school(s) whose attendance zone contains a given location (within California).  It is built using Kotlin / Compose Multiplatform.  While it is primarily intended for Android use, it is also expected to run as a Desktop and iOS app, so should be written to be cross compatible with those.

## Requirements

There are two modes: foreground and background.

### Foreground

When the app is opened, there is an input field where the user can enter a latitude and longitude.  There is also a button that allows these fields to be filled using the current latitude and longitude.  Once these are filled, there is a "Lookup" button that finds the corresponding schools.  This is done by looking in the GeoJson file stored within this project.

### Background

In future there will be a background mode that monitors the device location and notifies when a new school district is entered.  This will not be implemented initially, but should be kept in mind as a future use case.
