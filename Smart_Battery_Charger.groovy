/*=============================================================================
 *
 *  Copyright 2021 Andrew Kershaw, alksoft
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *
 *  SMART BATTERY CHARGER
 *  This app subscribes to a power metering smart plug and allows the user to set a minimum threshold below which the
 *  smart plug will shut off. For battery charging applications, this assumes that the minimum threshold corresponds 
 *	with the steady-state power when the battery reaches full or trickle charge and preserves the battery's life by
 *	shutting off the smart plug.
 *
 *  Version 20210103
 *===========================================================================*/

definition(
	name: "Smart Battery Charger",
	author: "Andrew Kershaw",
	category: "Convenience",
	description: "Stop overcharging batteries!",
	iconUrl: "",
	iconX2Url: "",
	iconX3Url: "",
	namespace: "alksoft"	
)

preferences {
	
	// The user should select a smart plug with a power monitoring capability
	section("Which power monitor do you wish to control?") {
		paragraph image: "https://images-na.ssl-images-amazon.com/images/I/41lQhDjdCQL._AC_SL1000_.jpg",
					"Select a power metering mart plug."
	    // For SmartThings, this is "capability.power". For Hubitat, it's "capability.powerMeter"
		input "theSmartPlug", "capability.powerMeter", required: true, title: "Which power monitor?", multiple: false
	}
	
	// Allow the user to specify the minimum wattage to trigger a shut-off
	// Todo: should it be just one measurement, a moving average of X samples, a duration below a value, or convergence?
	section("Power parameters:") {
		input "triggerPowerLevel", "decimal", title: "Minimum power? (Amps)", defaultValue: "1.5"
	}
}


def getDefaultPower() {
	def dPwr = [-99.9, -99.9, -99.9]
	return dPwr
}

// Runs when the user first installs the app
def installed() {
	//log.debug "in installed"
	initialize()
}

// Runs when the user changes any app preferences
def updated() {
	//log.debug "in updated"
	unsubscribe() //Unsubscribe from everything
	initialize()
}

def initialize() {
	//log.debug "in initialized"
	state.powerSamples = defaultPower
	state.aboveThreshold = false
	subscribe(theSmartPlug,"power",checkPower)
	log.info "Initialized with a monitoring threshold of $triggerPowerLevel Watts"
}

def reInit() {
	state.powerSamples = defaultPower
	state.aboveThreshold = false
}

def checkPower(evt) {
	//log.debug "in checkPower"
	//log.debug "The new power: ${evt.value}"
	//def thisPowerIdx = -99
	def newPower = evt.getFloatValue()
	if (newPower < 0.001) {
		//log.debug "Power is very low. Assuming switch is off and re-initializing"
		reInit()
	} else {
	
		//Check if any of the power samples are -99.9.  These are artificial, invalid power samples
	
		//log.debug "checking for stored invalid power"
		def invalidPower = false
		if (state.powerSamples[0] == -99.9) {
			invalidPower = true
			//thisPowerIdx = 0
		}
		if (state.powerSamples[1] == -99.9) {
			invalidPower = true
			//thisPowerIdx = 1	
		}
		if (state.powerSamples[2] == -99.9) {
			invalidPower = true
			//thisPowerIdx = 2
		}
	
		//log.debug "Stored invalid power found: $invalidPower"
		//if (invalidPower) {log.debug "at position $thisPowerIdx"}
	
		//log.debug "Checking if newPower is above threshold"
		if (newPower > triggerPowerLevel) { 
			//log.debug "Power above the trigger threshold $triggerPowerLevel detected"
			state.aboveThreshold = true 
		}
	
        log.debug "Storing new power: $newPower Watts"
		// Shift the old power samples down one position and save the new power sample at the back of the array
		state.powerSamples[0] = state.powerSamples[1]
		state.powerSamples[1] = state.powerSamples[2]
		state.powerSamples[2] = newPower 
		
		def batteryFullSamples = checkBatteryFull(state.powerSamples,triggerPowerLevel)
		if (batteryFullSamples?.size() == 3 && !invalidPower) {
			log.info "Three successive occurrences below trigger power, turning off switch..."
			//Turn off the switch and set all values back to their defaults to be ready for next run
			theSmartPlug.off()
			state.powerSamples = defaultPower
			state.aboveThreshold = false
		}
	}
}

def checkBatteryFull(nums, triggerPowerLevel) {
	nums.findAll { it <= triggerPowerLevel }
}
