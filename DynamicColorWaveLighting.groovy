/**
 * Dynamic Color Wave Lighting
 *
 * May 17, 2026
 *
 * =================================================================================
 * AVAILABLE COLOR SCHEMES & DESCRIPTIONS:
 * =================================================================================
 * * Aurora Borealis        - Cool greens, shifting teals, and occasional indigo/violet accents.
 * * Chinatown              - Deep striking reds, rich golds, and warm oranges cycling in waves.
 * * Ember & Hearth         - Slow amber-orange fireplace-like glow with structural flame flicker.
 * * Ocean Drift            - Calm aquamarine and sapphire deep blues shifting gently like water.
 * * Candlelight            - Ultra-warm white-to-gold tones with a delicate fluttering candle glow.
 * * Enchanted Forest       - Deep rich forest greens intertwined with dark, immersive woodland purples.
 * * Synthwave              - Retro hot pink, electric purple, and vibrant neon cyan pulses.
 * * Pride ROYGBIV          - Vividly cycles sequentially through full Red, Orange, Yellow, Green, Blue, Violet.
 * * Japanese Cherry Blossom- Soft, elegant pastel pinks, snowy whites, and rose-tinted blossom tones.
 * * Primary Colors         - Direct, punchy alternation between pure Red, pure Blue, and pure Yellow.
 * * Blue Monday            - Moody, shifting layers of rich blues, ranging from crisp electric ice to deep midnight tones.
 * =================================================================================
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

import groovy.json.JsonSlurper

definition(
    name: "Dynamic Color Wave Lighting",
    namespace: "custom",
    author: "RichRich2112",
    description: "Simulates shifting ambient environments by cycling colors on Z-Wave, Zigbee, and Matter RGBW bulbs. Supports button-hold theme switching and mobile push notifications.",
    category: "Lighting",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: "")

preferences {
    section("Logging") {
        input "enableDebugLogging", "bool", title: "Enable debug logging?", defaultValue: false, required: false
    }
    section("Select color bulbs for effect") {
        input "colorBulbs", "capability.colorControl", title: "Color Bulbs", multiple: true, required: true
    }
    section("Theme Configuration") {
        input "colorScheme", "enum", title: "Select Color Scheme", required: true, defaultValue: "Aurora Borealis",
            options: [
                "Aurora Borealis", 
                "Chinatown", 
                "Ember & Hearth", 
                "Ocean Drift", 
                "Candlelight", 
                "Enchanted Forest", 
                "Synthwave",
                "Pride ROYGBIV",
                "Japanese Cherry Blossom",
                "Primary Colors",
                "Blue Monday"
            ]
    }
    section("") {
        input "bulbInterval", "number", title: "Set a bulb every (seconds)", required: true, defaultValue: 3
    }
    section("") {
        input "cyclePause", "number", title: "Then pause (seconds)", required: true, defaultValue: 3
    }
    section("") {
        input "auroraLevel", "number", title: "Base Effect Brightness", required: true, defaultValue: 100, range: "1..100"
    }
    section("Start/Stop Control (Switch)") {
        input "controlSwitch", "capability.switch", title: "Switch to Start/Stop Effect (optional)", required: false, multiple: false
    }
    section("Start & Theme Cycle Control (Button)") {
        input "startButtonDevice", "capability.pushableButton", title: "Button Device to Control Effect (optional)", required: false, multiple: false
        input "startButtonNumber", "number", title: "Button Number to Start", required: false, defaultValue: 1
        input "startButtonAction", "enum", title: "Button Action to Start", options: ["pushed", "held", "doubleTapped"], required: false, defaultValue: "pushed"
        input "cycleButtonNumber", "number", title: "Button Number to Cycle Themes (Hold)", required: false, defaultValue: 1
        input "notificationDevice", "capability.notification", title: "Send theme change notification to this device (optional)", required: false, multiple: true
    }
    section("Stop Control (Button)") {
        input "stopButtonDevice", "capability.pushableButton", title: "Button Device to Stop Effect (optional)", required: false, multiple: false
        input "stopButtonNumber", "number", title: "Button Number to Stop", required: false, defaultValue: 1
        input "stopButtonAction", "enum", title: "Button Action to Stop", options: ["pushed", "held", "doubleTapped"], required: false, defaultValue: "pushed"
    }
}

def installed() {
    initialize()
}

def updated() {
    unschedule()
    unsubscribe()
    initialize()
}

def uninstalled() {
    unschedule()
    unsubscribe()
}

def initialize() {
    if (controlSwitch) {
        subscribe(controlSwitch, "switch.on", onSwitchOn)
        subscribe(controlSwitch, "switch.off", onSwitchOff)
    }
    if (startButtonDevice && startButtonAction && startButtonNumber) {
        subscribe(startButtonDevice, startButtonAction, onButtonEvent)
        // Ensure app explicitly listens for the held action on our primary button device
        subscribe(startButtonDevice, "held", onButtonEvent)
    }
    if (stopButtonDevice && stopButtonAction && stopButtonNumber) {
        subscribe(stopButtonDevice, stopButtonAction, onStopButtonEvent)
    }

    if (colorBulbs) {
        colorBulbs.each { bulb ->
            subscribe(bulb, "switch", onBulbStateChange)
            subscribe(bulb, "color", onBulbStateChange)
            subscribe(bulb, "colorTemperature", onBulbStateChange)
        }
    }
    state.auroraActive = false
    state.auroraFirstCyclePrime = true
    
    // Explicitly purge stale color memories on initialization
    state.auroraBaseHue = null
    state.auroraBaseSat = null
    state.auroraStep = 0
    state.auroraOrder = null
}

void handleButtonEvent(evt, actionKey, numberKey, isStart) {
    def action = settings["${actionKey}"] ?: "pushed"
    def btnNum = (settings["${numberKey}"] ?: 1) as Integer
    debugLog "Button event received: name=${evt.name}, value=${evt.value}, data=${evt.data}, device=${evt.device}, action=${action}, btnNum=${btnNum}"
    def eventAction = evt.name
    def eventBtnNum = null
    try {
        if (evt.value) {
            eventBtnNum = evt.value as Integer
            debugLog "Parsed buttonNumber from evt.value: ${eventBtnNum}"
        } else if (evt.data) {
            def json = groovy.json.JsonSlurper.newInstance().parseText(evt.data)
            eventBtnNum = json.buttonNumber as Integer
            debugLog "Parsed buttonNumber from evt.data: ${eventBtnNum}"
        } else {
            eventBtnNum = 1
            debugLog "Fallback to buttonNumber 1"
        }
    } catch (e) {
        eventBtnNum = 1
        debugLog "Exception parsing button number: ${e}"
    }
    
    // Logic implementation: Intercept Hold action on designated cycle button mid-animation loop
    if (isStart && eventAction == "held" && eventBtnNum == (settings.cycleButtonNumber?.toInteger() ?: 1)) {
        if (state.auroraActive) {
            log.info "Theme cycle event matched via button hold."
            
            // Hardcoded static tracker to prevent driver-exclusive getSettingDefinition missing method errors
            def themeList = [
                "Aurora Borealis", 
                "Chinatown", 
                "Ember & Hearth", 
                "Ocean Drift", 
                "Candlelight", 
                "Enchanted Forest", 
                "Synthwave",
                "Pride ROYGBIV",
                "Japanese Cherry Blossom",
                "Primary Colors",
                "Blue Monday"
            ]
            
            def currentIndex = themeList.indexOf(settings.colorScheme)
            if (currentIndex == -1) currentIndex = 0
            
            def nextIndex = (currentIndex + 1) % themeList.size()
            def nextTheme = themeList[nextIndex]
            
            log.info "Cycling profile preference from '${settings.colorScheme}' to '${nextTheme}'"
            app.updateSetting("colorScheme", [type: "enum", value: nextTheme])
            
            // Push notification logic wrapper
            if (notificationDevice) {
                notificationDevice.deviceNotification("Dynamic Wave Lighting changed to: ${nextTheme}")
            }
            
            // Rapid recalculation: Clear current pacing metrics but keep original saved baseline states safe
            unschedule()
            state.auroraStep = 0
            state.auroraOrder = null
            state.auroraBaseHue = null
            state.auroraBaseSat = null
            
            auroraLoop()
        } else {
            debugLog "Theme cycle requested via hold, but animation loop is currently stopped. Ignoring."
        }
        return
    }

    if (settings.enableDebugLogging) log.debug "Comparing eventAction=${eventAction} to action=${action}, eventBtnNum=${eventBtnNum} to btnNum=${btnNum}"
    
    // Fall back to standard Start/Stop button event checking
    if (eventAction == action && eventBtnNum == btnNum) {
        if (isStart) {
            debugLog "Button event matched, starting animation."
            if (!state.auroraActive) {
                state.auroraActive = true
                state.auroraStarted = false
                state.auroraRestoreOnStop = false
                runIn(2, auroraLoop)
            }
        } else {
            debugLog "Stop button event matched, stopping animation."
            if (state.auroraActive) {
                stopAll()
            }
        }
    }
}

def onBulbStateChange(evt) {
    def suppressMap = state.auroraSuppressEventsUntil ?: [:]
    def bulbId = evt.device?.id
    if (bulbId && suppressMap[bulbId] && now() < suppressMap[bulbId]) {
        debugLog "Suppressed bulb event for ${evt.device.displayName} ${evt.name}: ${evt.value}"
        return
    }
    def lastHueMap = state.auroraLastHue ?: [:]
    if (evt.name == 'switch' && evt.value == 'off') {
        debugLog "Bulb ${evt.device.displayName} reported OFF or cut power."
        
        // Count how many selected bulbs are still physically reporting as 'on'
        def bulbsOn = colorBulbs.count { it.currentSwitch == "on" }
        debugLog "Active tracking check: ${bulbsOn} out of ${colorBulbs.size()} bulbs remain on."
        
        if (bulbsOn == 0) {
            log.info "All bulbs are off. Fully shutting down automation loop cleanly."
            stopAll()
        } else {
            log.info "A bulb cut power, but ${bulbsOn} bulbs are still active. Continuing wave on remaining devices."
            // The loop will automatically bypass unresponsive devices on the next cycle iteration
        }
        return
    }
    if (evt.name == 'switch' && evt.value == 'on') {
        debugLog "Ignoring 'switch on' event for ${evt.device.displayName}"
        return
    }
    if (evt.name == 'colorMode' && evt.value == 'CT') {
        debugLog "Bulb set to CT mode externally (${evt.device.displayName}), aborting."
        stopAll()
        return
    }
    if (evt.name == 'hue') {
        def lastHue = lastHueMap[bulbId]
        def newHue = evt.value as Double
        if (lastHue != null && Math.abs(newHue - lastHue) > 5) {
            debugLog "Bulb hue changed externally (${evt.device.displayName} hue: ${evt.value}), aborting."
            stopAll()
            return
        }
    }
    if (state.auroraSuppressAbort) {
        debugLog "Suppressing abort during first cycle for ${evt.device.displayName} ${evt.name}: ${evt.value}"
        return
    }
    debugLog "Ignoring non-abort event for ${evt.device.displayName} ${evt.name}: ${evt.value}"
    return
}

def auroraLoop() {
    debugLog "auroraLoop called: colorBulbs=${colorBulbs}, state.auroraActive=${state.auroraActive}"
    if (!colorBulbs) { debugLog "No color bulbs selected"; return }
    if (!state.auroraActive) { debugLog "Effect not active"; return }

    if (!state.auroraStarted) {
        if (state.auroraRestoreOnStop) {
            debugLog "Capturing bulb states at animation start"
            captureBulbStates()
        }
        state.auroraStarted = true
        state.auroraSuppressAbort = true
    }

    def bulbIntervalMs = ((settings.bulbInterval ?: 3) * 1000).toInteger()
    def cyclePauseSec = settings.cyclePause ?: 3
    def bulbs = colorBulbs
    
	// Do not affect any bulbs except the selected ones
    // def allColorBulbs = location.allDevices.findAll { it.hasCapability("ColorControl") }
    // def unselectedBulbs = allColorBulbs - bulbs
    // unselectedBulbs.each { it.off() }

    if (!state.auroraStep) state.auroraStep = 0
    def step = state.auroraStep as Integer
    def randomOrder
    if (!state.auroraOrder) {
        randomOrder = bulbs.sort{new Random().nextInt()}
        state.auroraOrder = randomOrder.collect{ it.id }
    } else {
        randomOrder = state.auroraOrder.collect { id -> bulbs.find{ it.id == id } }.findAll{ it }
    }

    def theme = settings.colorScheme ?: "Aurora Borealis"

    if (state.auroraBaseHue == null || state.auroraBaseSat == null) {
        switch(theme) {
            case "Chinatown":
                state.auroraBaseHue = 0 
                state.auroraBaseSat = 95
                break
            case "Ember & Hearth":
                state.auroraBaseHue = 8 
                state.auroraBaseSat = 90
                break
            case "Ocean Drift":
                state.auroraBaseHue = 55 
                state.auroraBaseSat = 90
                break
            case "Candlelight":
                state.auroraBaseHue = 10 
                state.auroraBaseSat = 40 
                break
            case "Enchanted Forest":
                state.auroraBaseHue = 35 
                state.auroraBaseSat = 95
                break
            case "Synthwave":
                state.auroraBaseHue = 85 
                state.auroraBaseSat = 95
                break
            case "Pride ROYGBIV":
                state.auroraBaseHue = 0 
                state.auroraBaseSat = 100
                break
            case "Japanese Cherry Blossom":
                state.auroraBaseHue = 96 
                state.auroraBaseSat = 35 
                break
            case "Primary Colors":
                state.auroraBaseHue = 0 
                state.auroraBaseSat = 100
                break
            case "Blue Monday":
                state.auroraBaseHue = 62 
                state.auroraBaseSat = 95
                break
            case "Aurora Borealis":
            default:
                state.auroraBaseHue = 50 
                state.auroraBaseSat = 85
                break
        }
    }

    def rawLevel = settings.auroraLevel ?: 100
    def baseLevel = Math.max(1, Math.min(100, rawLevel as Integer))

    // Armored initialization values optimized for Linkind RGB spectrum
    def primeHue = (theme == "Blue Monday") ? 63 : state.auroraBaseHue
    def primeSat = (theme == "Blue Monday") ? 95 : state.auroraBaseSat

    if (step == 0) {
        if (state.auroraStarted && state.auroraFirstCyclePrime != false) {
            bulbs.each { bulb ->
                try {
                    suppressEventsFor(bulb)
                    if (theme == "Candlelight") {
                        clearLastHue(bulb)
                        bulb.setColorTemperature(2200)
                        bulb.setLevel(Math.max(6, (baseLevel * 0.35) as Integer))
                    } else {
                        setLastHue(bulb, primeHue as Double)
                        bulb.setColor([hue: primeHue, saturation: primeSat, level: baseLevel])
                    }
                } catch (e) {
                    debugLog "setColor failed during prime for ${bulb.displayName}: ${e}"
                }
            }
            state.auroraFirstCyclePrime = false
        }
    }
    
    if (step < randomOrder.size()) {
        def bulb = randomOrder[step]
        
        // Armored fallback values setup explicitly before execution blocks
        def targetHue = (theme == "Blue Monday") ? 62 : state.auroraBaseHue
        def targetSat = (theme == "Blue Monday") ? 95 : state.auroraBaseSat
        def targetLevel = baseLevel

        switch(theme) {
            case "Aurora Borealis":
                def hueOffset = (step % 3 - 1) * (6 + new Random().nextInt(6))
                if (new Random().nextInt(10) > 7) { 
                    targetHue = 78 
                } else {
                    targetHue = (state.auroraBaseHue + hueOffset) % 100
                }
                targetSat = state.auroraBaseSat - new Random().nextInt(10)
                break

            case "Chinatown":
                def roll = step % 3
                if (roll == 0) targetHue = 0      
                else if (roll == 1) targetHue = 8  
                else targetHue = 15                
                targetSat = 95 - new Random().nextInt(10)
                break

            case "Ember & Hearth":
                targetHue = (state.auroraBaseHue + new Random().nextInt(5)) % 100
                targetSat = 90 - new Random().nextInt(10)
                targetLevel = Math.max(15, baseLevel - new Random().nextInt(35)) 
                break

            case "Ocean Drift":
                def driftOffset = (step * 5) % 20
                targetHue = (50 + driftOffset) % 100
                targetSat = 85 + new Random().nextInt(15)
                break

            case "Candlelight":
                // Shift strictly within Linkind's ultra-warm white hardware spectrum (2000K to 2400K)
                def targetKelvin = 2000 + new Random().nextInt(400)
                
                // Scale base brightness back heavily so it stays an ambient, cozy glow
                def candleBase = (baseLevel * 0.35) as Integer
                
                // Allow a deeper, faster drop to mimic a delicate, fluttering flame
                targetLevel = Math.max(6, candleBase - new Random().nextInt(20)) 
                
                debugLog "Setting ${bulb.displayName} to Candle White: ${targetKelvin}K at level ${targetLevel}"
                try {
                    suppressEventsFor(bulb)
                    // Clear out any stale RGB hue tracking to keep the driver clean
                    clearLastHue(bulb)
                    
                    // Fire the explicit Color Temperature command to activate pure warm white hardware
                    bulb.setColorTemperature(targetKelvin)
                    bulb.setLevel(targetLevel)
                } catch (e) {
                    debugLog "Candlelight CT command failed: ${e}"
                }
                break

            case "Enchanted Forest":
                if (step % 2 == 0) {
                    targetHue = 33 + new Random().nextInt(6)
                    targetSat = 95
                } else {
                    targetHue = 78 + new Random().nextInt(6)
                    targetSat = 85
                }
                targetLevel = Math.max(10, (baseLevel * 0.75) as Integer)
                break

            case "Synthwave":
                def synthRoll = step % 3
                if (synthRoll == 0) targetHue = 92
                else if (synthRoll == 1) targetHue = 76
                else targetHue = 53
                targetSat = 95
                break

            case "Pride ROYGBIV":
                def prideRoll = step % 6
                if (prideRoll == 0) targetHue = 0     
                else if (prideRoll == 1) targetHue = 7  
                else if (prideRoll == 2) targetHue = 16 
                else if (prideRoll == 3) targetHue = 35 
                else if (prideRoll == 4) targetHue = 64 
                else targetHue = 79                     
                targetSat = 100
                break

            case "Japanese Cherry Blossom":
                def blossomRoll = step % 3
                if (blossomRoll == 0) {
                    targetHue = 96 
                    targetSat = 35
                } else if (blossomRoll == 1) {
                    targetHue = 98 
                    targetSat = 50
                } else {
                    targetHue = 10 
                    targetSat = 12
                }
                break

            case "Primary Colors":
                def primaryRoll = step % 3
                if (primaryRoll == 0) targetHue = 0     
                else if (primaryRoll == 1) targetHue = 64 
                else targetHue = 16                     
                targetSat = 100
                break

            case "Blue Monday":
                def blueRoll = step % 3
                if (blueRoll == 0) {
                    // STEP 1: Calibrated Ice Blend (Blue LED + 6500K Cool White)
                    targetHue = 64  
                    targetSat = 40  
                    
                    // Drop the brightness heavily to compensate for the extra white LED output
                    targetLevel = Math.max(10, (baseLevel * 0.25) as Integer)
                    
                    try {
                        suppressEventsFor(bulb)
                        setLastHue(bulb, targetHue as Double)
                        bulb.setColor([hue: targetHue, saturation: targetSat, level: targetLevel, colorTemperature: 6500])
                    } catch (e) {
                        // Fallback handling for stubborn drivers
                        bulb.setColorTemperature(6500)
                        bulb.setColor([hue: targetHue, saturation: targetSat, level: targetLevel])
                    }
                } else if (blueRoll == 1) {
                    // STEP 2: Solid True Blue / Cobalt (RGB Only)
                    targetHue = 63  
                    targetSat = 95  
                    targetLevel = baseLevel // Full base brightness
                    bulb.setColor([hue: targetHue, saturation: targetSat, level: targetLevel])
                } else {
                    // STEP 3: Deep Sapphire / Midnight Blue (RGB Only)
                    targetHue = 67  
                    targetSat = 100 
                    // Dims the deep sapphire slightly for rich contrast, but keeps it higher than the ice step
                    targetLevel = Math.max(15, (baseLevel * 0.70) as Integer) 
                    bulb.setColor([hue: targetHue, saturation: targetSat, level: targetLevel])
                }
                break
        }

        debugLog "Setting ${bulb.displayName} [${theme}] to hue:${targetHue} sat:${targetSat} level:${targetLevel}"
        try {
            suppressEventsFor(bulb)
            setLastHue(bulb, targetHue as Double)
            bulb.setColor([hue: targetHue, saturation: targetSat, level: targetLevel])
        } catch (e) {
            debugLog "setColor failed for ${bulb.displayName}: ${e}"
        }
        state.auroraStep = step + 1
        runInMillis(bulbIntervalMs, auroraLoop)
    } else {
        if (state.auroraSuppressAbort) {
            runIn(Math.round(cyclePauseSec) as int, clearAuroraSuppressAbort)
        }
        resetCycleState()
        runIn(Math.round(cyclePauseSec) as int, auroraLoop)
    }
}

def clearAuroraSuppressAbort() {
    state.auroraSuppressAbort = false
    debugLog "Abort suppression cleared after cycle pause."
}

def captureBulbStates() {
    state.savedBulbStates = [:]
    colorBulbs.each { bulb ->
        try {
            def bulbState = [
                switch: bulb.currentSwitch,
                hue: bulb.currentHue,
                saturation: bulb.currentSaturation,
                level: bulb.currentLevel,
                colorMode: bulb.currentColorMode
            ]
            state.savedBulbStates[bulb.id] = bulbState
        } catch (e) {
            debugLog "Could not capture state for ${bulb.displayName}: ${e}"
        }
    }
}

def restoreBulbStates() {
    if (!state.savedBulbStates) return
    colorBulbs.each { bulb ->
        def prev = state.savedBulbStates[bulb.id]
        if (prev) {
            try {
                if (prev.switch == "off") {
                    suppressEventsFor(bulb)
                    clearLastHue(bulb)
                    bulb.off()
                } else {
                    if (prev.colorMode == "CT" && bulb.hasCommand("setColorTemperature")) {
                        def ct = bulb.currentColorTemperature ?: 4000
                        suppressEventsFor(bulb)
                        clearLastHue(bulb)
                        bulb.setColorTemperature(ct)
                        bulb.setLevel(prev.level)
                    } else {
                        suppressEventsFor(bulb)
                        setLastHue(bulb, prev.hue as Double)
                        bulb.setColor([hue: prev.hue, saturation: prev.saturation, level: prev.level])
                    }
                }
            } catch (e) {
                debugLog "Could not restore state for ${bulb.displayName}: ${e}"
            }
        }
    }
    state.savedBulbStates = null
}

def onSwitchOn(evt) {
    if (!state.auroraActive) {
        initialize()
        state.auroraActive = true
        state.auroraStarted = false
        state.auroraRestoreOnStop = true
        runIn(2, auroraLoop)
    }
}

def onSwitchOff(evt) {
    debugLog "Control switch turned off. Stopping effect and turning off all lights."
    if (state.auroraActive) {
        state.auroraActive = false
        state.auroraStarted = false
        unsubscribeBulbEvents()
    }
    if (colorBulbs) {
        colorBulbs.each { bulb ->
            try {
                suppressEventsFor(bulb)
                clearLastHue(bulb)
                bulb.off()
            } catch (e) {
                debugLog "Failed to turn off ${bulb.displayName}: ${e}"
            }
        }
    }
}

private void suppressEventsFor(bulb, long ms = 1000) {
    def map = state.auroraSuppressEventsUntil ?: [:]
    map[bulb.id] = now() + ms
    state.auroraSuppressEventsUntil = map
}

private void setLastHue(bulb, double hue) {
    def map = state.auroraLastHue ?: [:]
    map[bulb.id] = hue
    state.auroraLastHue = map
}

private void clearLastHue(bulb) {
    def map = state.auroraLastHue ?: [:]
    map.remove(bulb.id)
    state.auroraLastHue = map
}

private void debugLog(String msg) {
    if (settings.enableDebugLogging) log.debug(msg)
}

private void resetCycleState() {
    state.auroraStep = 0
    state.auroraOrder = null
    state.auroraBaseHue = null
    state.auroraBaseSat = null
}

def onButtonEvent(evt) {
    handleButtonEvent(evt, 'startButtonAction', 'startButtonNumber', true)
}

def onStopButtonEvent(evt) {
    handleButtonEvent(evt, 'stopButtonAction', 'stopButtonNumber', false)
}

private void unsubscribeBulbEvents() {
    if (colorBulbs) {
        colorBulbs.each { bulb ->
            unsubscribe(bulb, "switch")
            unsubscribe(bulb, "color")
            unsubscribe(bulb, "colorTemperature")
        }
    }
}

private void stopAll() {
    if (state.auroraActive) {
        state.auroraActive = false
        state.auroraStarted = false
        if (state.auroraRestoreOnStop) restoreBulbStates()
        unsubscribeBulbEvents()
    }
}