/**
 *  Starling Hub Support for Hubitat
 *  Schwark Satyavolu
 *
 */

 def version() {"0.1.1"}

import hubitat.helper.InterfaceUtils
import java.security.MessageDigest

def appVersion() { return "4.0" }
def appName() { return "Starling Hub Support" }

definition(
    name: "${appName()}",
    namespace: "schwark",
    author: "Schwark Satyavolu",
    description: "This adds support for Starling Hub",
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
            input "secure", "bool", title: "Enable HTTPS", defaultValue: true
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
                    input("face${i}", "enum", title: getFormat("section", "Face:"), options: state.faces)
                }
            }
        }
    }
}

def installed() {
    initialize()
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
    refresh()
}

def initialize() {
    unschedule()
    schedule('*/5 * * ? * *', refresh)
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
    json?.devices?.each() {
        if(!types.contains(it.type)) return
        createChildDevice(it.name, it.id, 'bell')
    }
}

def makeChildDeviceId(name, type) {
    return "STARLING-${type.toUpperCase()}-${name}"
}

def processDeviceData(json) {
    debug(json, "processDeviceData()")
    def facenum = 1
    def id = json?.properties?.id
    def doorbell = json?.properties?.doorbellPushed
    if(doorbell) {
        debug("bell event ${id} with value ${doorbell}")
        def cd = getChildDevice(makeChildDeviceId(id, 'bell'))
        if(cd) cd.sendEvent(name: 'pushed', value: 1)
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
                if(cd) {
                    cd.updateDataValue(name: 'seenAt', value: json?.properties?.name)
                    cd.sendEvent(name: 'pushed', value: 1)
                }
            }
        } 
    }
}

def getDeviceDetails() {
    def children = getAllChildDevices()
    children?.each() {
        def idparts = it.deviceNetworkId.split("-")
        def type = idparts[1].toLowerCase()
        def id = idparts[-1]
        if(type != 'face') getDeviceData(id)
    }
}

def refresh() {
    getDeviceDetails()
    pauseExecution(1000)
}

def parseResponse(cmd, data) {
    if('list' == cmd) {
        processDevices(data)
    } else if('details' == cmd) {
        processDeviceData(data)
    }
}

def getBase() {
    def dashIp = ip.replaceAll(/\./,'-')
    secure ? "https://${dashIp}.local.starling.direct:3443/api/connect/v1" : "http://${ip}:3080/api/connect/v1"
}

def starlingRequest(cmd, params=null) {
    def commands = [
        list: [uri: 'devices'],
        details: [uri: "devices/${params?.id}"]
    ]
    def headers = [
    ]
    def base = getBase()
    def url = "${base}/${commands[cmd].uri}?key=${key}"
    debug("${url}")

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
            log.info("Hubitat Starling Button: id: ${deviceId} label: ${label} ${created}")
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

void componentRefresh(cd) {
    debug("received refresh request from ${cd.displayName}")
    def idparts = cd.deviceNetworkId.split("-")
    def type = idparts[1].toLowerCase()
    def id = idparts[-1]
    if(type != 'face') getDeviceDetails(id)
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