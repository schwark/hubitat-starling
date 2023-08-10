# Google Nest Doorbell via Starling Hub for Hubitat

This is an integration for Hubitat hubs to enable interfacing with Google Nest Doorbells via Starling Hubs. 

To use, go to your Hubitat hub, Go to Developer tools / Apps Code / Add New App and paste and save this file

https://raw.githubusercontent.com/schwark/hubitat-starling/main/starling.groovy

Go to Apps / Add User App / Nest Doorbell via Starling Hub

Pick the number of buttons to create for identified faces (one button is created for each face you want to be notified when seen at any of the doorbells in your house). One button will also be created for each doorbell in your house.

Open the Starling app / Developer Connect / Enable HTTP port and create an API Key. Also do the Google connect authorization so your Starling Hub has access to your Google Home devices - this app only has support for doorbells for now.

Enter the API key, and the IP address of your Starling hub into the preferences page. Then click on Choose Faces - one dropdown should show up for each face you want a button created for - pick the name for each button

A button named ```<Doorbell Name>``` will be created for each doorbell. A push event is generated on this button anytime the doorbell is rung

A button named ```<Face Name>``` will be created for each identified face selected. A push event is generated on this button anytime the person is seen on ANY of the doorbells in the house







