/**
 *  Starling Hub Support for Hubitat
 *  Schwark Satyavolu
 *
 */

 def version() {"0.1.1"}

import hubitat.helper.InterfaceUtils
import java.security.MessageDigest

def appVersion() { return "4.0" }
def appName() { return "Nest Doorbell via Starling Hub" }

definition(
    name: "${appName()}",
    namespace: "schwark",
    author: "Schwark Satyavolu",
    description: "This adds support for Google Nest Doorbells via Starling Hub",
    category: "Convenience",
    iconUrl: "https://is5-ssl.mzstatic.com/image/thumb/Purple122/v4/8c/69/97/8c699798-a0f2-c5b5-6a8d-4553d7b8babe/AppIcon-1x_U007emarketing-0-7-0-85-220.png/512x512bb.jpg",
    iconX2Url: "https://is5-ssl.mzstatic.com/image/thumb/Purple122/v4/8c/69/97/8c699798-a0f2-c5b5-6a8d-4553d7b8babe/AppIcon-1x_U007emarketing-0-7-0-85-220.png/512x512bb.jpg",
    singleInstance: true,
    importUrl: "https://raw.githubusercontent.com/schwark/hubitat-starling/main/starling.groovy"
)

preferences {
    page(name: "mainPage")
    page(name: "configPage")
}

def md5(String s){
    MessageDigest.getInstance("MD5").digest(s.bytes).encodeHex().toString()
}

def getFormat(type, myText=""){
    if(type == "section") return "<div style='color:#78bf35;font-weight: bold'>${myText}</div>"
    if(type == "hlight") return "<div style='color:#78bf35'>${myText}</div>"
    if(type == "header") return "<div style='color:#ffffff;background-color:#392F2E;text-align:center'>${myText}</div>"
    if(type == "redhead") return "<div style='color:#ffffff;background-color:red;text-align:center'>${myText}</div>"
    if(type == "line") return "\n<hr style='background-color:#78bf35; height: 2px; border: 0;'></hr>"
    if(type == "centerBold") return "<div style='font-weight:bold;text-align:center'>${myText}</div>"    
    
}

def mainPage(){
    dynamicPage(name:"mainPage",install:true, uninstall:true){
        section {
            input "debugMode", "bool", title: "Enable debugging", defaultValue: true
            input "secure", "bool", title: "Enable HTTPS", defaultValue: false
            input "httpPort", "text", title: "HTTP Port", defaultValue: 3080
            input "httpsPort", "text", title: "HTTPS Port", defaultValue: 3443
            input("numFaces", "number", title: getFormat("section", "How many faces to create buttons for?:"), defaultValue: 0, submitOnChange: true, range: "0..25")
        }
        section(getFormat("header", "Step 1: Configure your hub")) {
            input "key", "text", title: "Starling Hub API Key", required: true
            input "ip", "text", title: "Starling Hub IP", submitOnChange: true, required: true
        }
        if(numFaces){
            section(getFormat("header", "Step 2: Configure Your Faces")){
                    href "configPage", title: "Configure Faces"
              }
        }
    }
}

def configPage(){
    updated()
    dynamicPage(name: "configPage", title: "Configure Faces:") {
        if(numFaces){
            for(i in 1..numFaces){
                section(getFormat("header", "Face ${i}")){
                    input("face${i}", "enum", title: getFormat("section", "Face:"), submitOnChange: true, options: state.faces)
                }
            }
        }
    }
}

def installed() {
    initialize()
    unschedule()
    schedule('*/5 * * ? * *', refresh)    
}

def updated() {
    debug("updated preferences")
    initialize()
    for(i in 1..numFaces) {
        def id = settings["face${i}"]
        if(id) {
            def name = state.faces[id]
            def nameId = md5(name)
            cd = createChildDevice(name, nameId, 'face')
        }
    }
    getDevices()
    pauseExecution(1000)
    getDeviceDetails()
}

def initialize() {
}

def uninstalled() {
    unschedule()
    def children = getAllChildDevices()
    log.info("uninstalled: children = ${children}")
    children.each {
        deleteChildDevice(it.deviceNetworkId)
    }
}

def processDevices(json) {
    debug(json, "processDevices()")
    def types = ['cam']
    state.devices = [:]
    json?.devices?.each() {
        if(!types.contains(it.type)) return
        state.devices[it.id] = it.name
    }
}

def makeChildDeviceId(name, type) {
    return "STARLING-${type.toUpperCase()}-${name}"
}

def doorbellPushed(cd, json) {
    if(cd) {
        def name = json?.properties?.name
        log.info("[Starling Hub] ${name} was rung")
        cd.sendEvent(name: 'pushed', value: 1)
    }
}

def faceSeen(cd, cam, name) {
    if(cd) {
        log.info("[Starling Hub] Doorbell saw ${name} at ${cam}")
        def lastSeen = cd.getDataValue('lastSeen') as Long
        if(!lastSeen || now() - lastSeen > 20*1000) {
            cd.updateDataValue('lastSeen', "${now()}")
            cd.updateDataValue('seenAt', cam)
            cd.sendEvent(name: 'pushed', value: 1)
        }
    }
}

def processDeviceStatus(json) {
    //debug(json, "processDeviceStatus()")
    def id = json?.properties?.id
    def cd = getChildDevice(makeChildDeviceId(id, 'bell'))
    if(json.properties?.doorbellPushed) doorbellPushed(cd, json)

    for(i in 1..numFaces) {
        def nameId = settings["face${i}"]
        def name = state.faces[nameId]
        if(json.properties?."faceDetected:${name}") {
            cd = getChildDevice(makeChildDeviceId(nameId, 'face'))
            faceSeen(cd, json?.properties?.name, name)
        }
    }
}

def processDeviceData(json) {
    debug(json, "processDeviceData()")
    def facenum = 1
    def id = json?.properties?.id
    def isDoorbell = json?.properties?.containsKey('doorbellPushed')
    if(isDoorbell) {
        debug("bell event ${id} with value ${doorbell}")
        cd = createChildDevice(json.properties.name, id, 'bell')
        if(json.properties?.doorbellPushed) doorbellPushed(cd, json)
    }
    json?.properties?.each() { k, v ->
        if(k.startsWith('faceDetected:')) {
            def name = k.replaceAll('faceDetected:','')
            debug("found face ${name}")
            def nameId = md5(name)
            state.faces = state.faces ?: [:]
            state.faces[nameId] = name
            if(v) then {
                def cd = getChildDevice(makeChildDeviceId(nameId, 'face'))
                faceSeen(cd, json?.properties?.name, name)
            }
        } 
    }
}

def getDeviceDetails() {
    state.devices?.each() { k, v ->
        getDeviceData(k)
    }
}

def getDeviceStatuses() {
    state.devices?.each() { k, v ->
        getDeviceStatus(k)
    }
}

def refresh() {
    getDeviceStatuses()
}

def parseResponse(cmd, data) {
    if('list' == cmd) {
        processDevices(data)
    } else if('details' == cmd) {
        processDeviceData(data)
    } else if('status' == cmd) {
        processDeviceStatus(data)
    }
}

def getBase() {
    def dashIp = ip.replaceAll(/\./,'-')
    secure ? "https://${dashIp}.local.starling.direct:${httpsPort}/api/connect/v1" : "http://${ip}:${httpPort}/api/connect/v1"
}

def starlingRequest(cmd, params=null) {
    def commands = [
        list: [uri: 'devices'],
        details: [uri: "devices/${params?.id}"],
        status: [uri: "devices/${params?.id}"]
    ]
    def headers = [
    ]
    def base = getBase()
    def url = "${base}/${commands[cmd].uri}?key=${key}"
    //debug("${cmd} -> ${url}")

    httpGet([uri: url], { parseResponse(cmd, it.data) } )
}

private createChildDevice(label, id, type) {
    def deviceId = makeChildDeviceId(id, type)
    def createdDevice = getChildDevice(deviceId)
    def name = "Starling Hub ${type.capitalize()}"

    if(!createdDevice) {
        try {
            def component = 'Generic Component Button Controller'
            // create the child device
            addChildDevice("hubitat", component, deviceId, [label : "${label}", isComponent: false, name: "${name}"])
            createdDevice = getChildDevice(deviceId)
            def created = createdDevice ? "created" : "failed creation"
            log.info("[Starling Hub] id: ${deviceId} label: ${label} ${created}")
        } catch (e) {
            logError("Failed to add child device with error: ${e}", "createChildDevice()")
        }
    } else {
        debug("Child device type: ${type} id: ${deviceId} already exists", "createChildDevice()")
        if(label && label != createdDevice.getLabel()) {
            createdDevice.setLabel(label)
            createdDevice.sendEvent(name:'label', value: label, isStateChange: true)
        }
        if(name && name != createdDevice.getName()) {
            createdDevice.setName(name)
            createdDevice.sendEvent(name:'name', value: name, isStateChange: true)
        }
    }
    return createdDevice
}

def getDevices() {
    starlingRequest('list')
}

def getDeviceData(id) {
    starlingRequest('details', [id: id])
}

def getDeviceStatus(id) {
    starlingRequest('status', [id: id])
}

void componentRefresh(cd) {
    debug("received refresh request from ${cd.displayName}")
    def idparts = cd.deviceNetworkId.split("-")
    def type = idparts[1].toLowerCase()
    def id = idparts[-1]
    if(type != 'face') getDeviceStatus(id)
}

def componentPush(cd) {
    debug("received push request from DN = ${cd.name}, DNI = ${cd.deviceNetworkId}")
}

private debug(logMessage, fromMethod="") {
    if (debugMode) {
        def fMethod = ""

        if (fromMethod) {
            fMethod = ".${fromMethod}"
        }

        log.debug("[Starling Hub] DEBUG: ${fMethod}: ${logMessage}")
    }
}

private logError(fromMethod, e) {
    log.error("[Starling Hub] ERROR: (${fromMethod}): ${e}")
}