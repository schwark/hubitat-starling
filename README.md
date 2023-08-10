# Sonos Favorites Support for Hubitat

This is an integration for Hubitat hubs to enable playing of Sonos favorites via a Switch for automation purposes. This app only lets you play from favorites, but allows you to play radio stations, playlists from Apple Music or Spotify etc. 

The built-in integration only works if you have the URI of the song you want to play, but does not work with URIs of playlists for example. Hence the need for this add-in - just add a song to your Sonos favorites in the Sonos app, and you can play that song/station/playlist anytime via a switch. 

A side benefit is that this add in will also effectively made a Sonos a switch that can be turned on or off (to start or stop music) if a switch is more convenient in automations.

To use, go to your Hubitat hub, Go to Developer tools / Apps Code / Add New App and paste and save this file

https://raw.githubusercontent.com/schwark/hubitat-sonos-favorites/main/sonos-favorites.groovy

Go to Apps / Add User App / Sonos Favorites Support

Pick the speakers you want to use with this app

Then click on Presets

This will now allow you to pick the number of preset switches you want to create - pick a number up to 25 (only the number you need, you can increase later as necessary)

Then for each preset, pick which speaker and which favorites from your Sonos favorites - if the station or media you want to play is not in Sonos Favorites, add it to your Sonos favorites.

A switch named ```<Sonos Speaker Name> Fave <Preset Num>``` will be created for each preset. Turning it on will play the selected favorite, and turning off will stop the music.





