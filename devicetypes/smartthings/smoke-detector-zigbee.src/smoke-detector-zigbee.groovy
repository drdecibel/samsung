/**
* WZB Natural Gas Sensor
*
* Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
* in compliance with the License. You may obtain a copy of the License at:
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
* on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
* for the specific language governing permissions and limitations under the License.
*
*/

metadata {
definition (name: "Smoke Detector Zigbee", namespace: "smartthings", author: "SmartThings") {

    capability "Configuration"
    capability "Smoke Detector"
    capability "Sensor"
    capability "Refresh"

    command "enrollResponse"


	fingerprint profileID: "C0C9", deviceID: "0104", inClusters: "0002,0003,0005", outClusters: "0001"
}

simulator {

}

preferences {
	input description: "This feature allows you to correct any temperature variations by selecting an offset. Ex: If your sensor consistently reports a temp that's 5 degrees too warm, you'd enter \"-5\". If 3 degrees too cold, enter \"+3\".", displayDuringSetup: false, type: "paragraph", element: "paragraph"
	input "tempOffset", "number", title: "Temperature Offset", description: "Adjust temperature by this many degrees", range: "*..*", displayDuringSetup: false
}

tiles {
	standardTile("smoke", "device.smoke", width: 2, height: 2) {
		state("clear", label:"clear", icon:"st.alarm.smoke.clear", backgroundColor:"#ffffff")
		state("detected", label:"SMOKE", icon:"st.alarm.smoke.smoke", backgroundColor:"#e86d13")
	}

    standardTile("configure", "device.configure", inactiveLabel: false, decoration: "flat") {
		state "default", action:"configuration.configure", icon:"st.secondary.configure"
	}

    standardTile("refresh", "device.refresh", inactiveLabel: false, decoration: "flat") {
		state "default", action:"refresh.refresh", icon:"st.secondary.refresh"
	}

	main (["smoke"])
	details(["smoke","refresh","configure"])
}
}

def parse(String description) {
log.debug "description: $description"

Map map = [:]
if (description?.startsWith('catchall:')) {
	map = parseCatchAllMessage(description)
}
else if (description?.startsWith('read attr -')) {
	map = parseReportAttributeMessage(description)
}
else if (description?.startsWith('zone status')) {
	map = parseIasMessage(description)
}

log.debug "Parse returned $map"
def result = map ? createEvent(map) : null

if (description?.startsWith('enroll request')) {
	List cmds = enrollResponse()
    log.debug "enroll response: ${cmds}"
    result = cmds?.collect { new physicalgraph.device.HubAction(it) }
}
return result
}

private Map parseCatchAllMessage(String description) {
Map resultMap = [:]
def cluster = zigbee.parse(description)
if (shouldProcessMessage(cluster)) {
log.debug "Parse $cluster"
}

return resultMap
}

private boolean shouldProcessMessage(cluster) {
// 0x0B is default response indicating message got through
// 0x07 is bind message
boolean ignoredMessage = cluster.profileId != 0x0104 || 
cluster.command == 0x0B ||
cluster.command == 0x07 ||
(cluster.data.size() > 0 && cluster.data.first() == 0x3e)
return !ignoredMessage
}

private Map parseReportAttributeMessage(String description) {
Map descMap = (description - "read attr - ").split(",").inject([:]) { map, param ->
def nameAndValue = param.split(":")
map += [(nameAndValue[0].trim()):nameAndValue[1].trim()]
}
log.debug "Desc Map: $descMap"

}

private Map parseIasMessage(String description) {
List parsedMsg = description.split(' ')
String msgCode = parsedMsg[2]

Map resultMap = [:]
switch(msgCode) {
    case '0x0020': // Clear
    	resultMap = getSmokeResult('clear')
        break

    case '0x0021': // Smoke
    	resultMap = getSmokeResult('detected')
        break

    case '0x0022': // Tamper Alarm
        break

    case '0x0023': // Battery Alarm
        break

    case '0x0024': // Supervision Report
    	resultMap = getSmokeResult('clear')
        break

    case '0x0025': // Restore Report
    	resultMap = getSmokeResult('detected')
        break

    case '0x0026': // Trouble/Failure
        break

    case '0x0028': // Test Mode
        break
}
return resultMap
}

private Map getSmokeResult(value) {
log.debug 'Gas Status'
def linkText = getLinkText(device)
def descriptionText = "${linkText} is ${value == 'detected' ? 'detected' : 'clear'}"
return [
name: 'smoke',
value: value,
descriptionText: descriptionText
]
}

def refresh() {	//read enrolled state and zone type from IAS cluster
[
"st rattr 0x${device.deviceNetworkId} ${endpointId} 0x0500 0", "delay 500",
"st rattr 0x${device.deviceNetworkId} ${endpointId} 0x0500 1"
]
}

def configure() {

String zigbeeId = swapEndianHex(device.hub.zigbeeId)
log.debug "Confuguring Reporting, IAS CIE, and Bindings."
def configCmds = [
	"zcl global write 0x500 0x10 0xf0 {${zigbeeId}}", "delay 200",
	"send 0x${device.deviceNetworkId} 1 ${endpointId}", "delay 1500",
    
    //"raw 0x500 {01 23 00 00 00}", "delay 200",
    //"send 0x${device.deviceNetworkId} 1 1", "delay 1500",
    
]
log.debug "configure: Write IAS CIE"
return configCmds // send refresh cmds as part of config
}

def enrollResponse() {
log.debug "Sending enroll response"
[

"raw 0x500 {01 23 00 00 00}", "delay 200",
"send 0x${device.deviceNetworkId} 1 ${endpointId}"

]
}
private hex(value) {
new BigInteger(Math.round(value).toString()).toString(16)
}

private String swapEndianHex(String hex) {
reverseArray(hex.decodeHex()).encodeHex()
}

private byte[] reverseArray(byte[] array) {
int i = 0;
int j = array.length - 1;
byte tmp;
while (j > i) {
tmp = array[j];
array[j] = array[i];
array[i] = tmp;
j--;
i++;
}
return array
}