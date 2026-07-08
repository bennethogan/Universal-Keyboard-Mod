# Universal Keyboard CC:Tweaked peripheral API

Intro here, later

## Attaching


local keyboard = peripheral.find("universal_keyboard")


## Events

While a computer is attached, the controller pushes keyboard events (fired when someone types on the
controller in-world):

| Event | Args | When |
|---|---|---|
| `key` | `keyCode` (number), `held` (boolean) | a key goes down |
| `key_up` | `keyCode` (number) | a key is released |
| `char` | `character` (string) | a character is typed |

```lua
while true do
  local ev, a, b = os.pullEvent()
  if ev == "key" then print("key down:", a, "held:", b) end
end
```

## Method reference

### Status
| Method | Returns | Notes |
|---|---|---|
| `isLinked()` | boolean | is the controller linked as a computer target |
| `isInRange()` | boolean | is its linked target in range |
| `getRange()` | number | configured controller range |
| `unlink()` | -- | clears the controller's link |

### Controller data (values handed back to the computer)
| Method | Returns | Notes |
|---|---|---|
| `getSablePose()` | table or `nil` | Sable ship pose + motion, or `nil` if Sable isn't installed / the controller isn't on a sublevel |
| `getSableValue(name)` | number | one Sable field by name; errors on an unknown name |
| `getVariables()` | table (1-based array) | all sequencer variables `V1...Vn` |
| `getVariable(i)` | number | one sequencer variable, `V1 = 1`; errors if out of range |
| `setVariable(i, value)` | -- | set a sequencer variable, `V1 = 1`; **requires the controller unlocked** |
| `getState()` | table | `{ linked, channel, sequencerRunning, locked, onSublevel }` |

`getSablePose()` / `getSableValue()` field names:

```
posX, posY, posZ          -- position (real-world coords of the sublevel)
velX, velY, velZ          -- linear velocity
angVelX, angVelY, angVelZ -- angular velocity (deg/s)
pitch, yaw, roll          -- orientation (degrees)
shipSizeX, shipSizeY, shipSizeZ
```

### Linked-peripheral passthrough
Read and drive whatever the controller is linked to, by **wireless channel #**

| Method | Returns | Notes |
|---|---|---|
| `getLinked()` | table | `{ [channel] = type }` for each linked CC peripheral |
| `getMethods(ch)` | table | `{ name = kind }`; kind is `"get"`, `"call"`, or an arg type (`"number"`, `"int"`, `"string"`, `"true/false"`) |
| `get(ch, method)` | number | read a numeric getter |
| `set(ch, method, value)` | — | drive a numeric setter; **requires unlocked** |
| `setString(ch, method, value)` | — | drive a single-arg setter of any type, `value` given as text (parsed to the setter's type — string/enum/int/boolean/number); **requires unlocked** |
| `call(ch, method)` | — | invoke a no-arg command (e.g. `"stop"`); **requires unlocked** |

## Locking

Controllers are locked by default when they are first placed down.

Reads are always allowed. **Writes** (`set`, `setString`, `call`, `setVariable`) require the
controller to be **unlocked**,  if it's locked to an owner, you will see:
`controller is locked to <owner>`

A computer has no player identity, so this keeps a computer from
driving someone else's locked battery. Unlock the controller for public use, by clicking the lock icon at the top left of the main menu of the controller

## Examples

**Read Sable telemetry:**
```lua
local kb = peripheral.find("universal_keyboard")
local pose = kb.getSablePose()
if pose then
  print(("yaw %.1f  alt %.1f  speed %.2f"):format(
    pose.yaw, pose.posY, math.sqrt(pose.velX^2 + pose.velY^2 + pose.velZ^2)))
end
```

**Discover and drive a linked motor:**
```lua
local kb = peripheral.find("universal_keyboard")
for ch, kind in pairs(kb.getLinked()) do
  print("channel " .. ch .. ": " .. kind)          -- e.g. "channel 1: electric_motor"
end
for name, kind in pairs(kb.getMethods(1)) do
  print(name, kind)                                  -- e.g. getSpeed get   setSpeed int
end
print("rpm:", kb.get(1, "getSpeed"))
kb.set(1, "setSpeed", 128)                           -- errors if the controller is locked
```

**A simple heading-hold loop (Sable + a linked steering peripheral):**
```lua
local kb = peripheral.find("universal_keyboard")
local target = 90
while true do
  local pose = kb.getSablePose()
  if pose then
    local err = (target - pose.yaw + 180) % 360 - 180
    kb.set(3, "setYawInput", math.max(-1, math.min(1, err / 45)))
  end
  sleep(0.1)
end
```

**Non-numeric setter / command:**
```lua
kb.setString(3, "setMode", "VECTOR")   -- string/enum setter
kb.call(3, "stop")                     -- no-arg command
```

## Notes for scripters

- **Return types:** tables for structs (Sable pose, state, linked list), 1-based arrays for variable
  lists, numbers/booleans otherwise.
- **Errors** are raised as Lua errors (`pcall` them if you want to handle "no peripheral on channel N",
  "controller is locked", or a setter's own error message).
- **Threading:** every method runs on the server tick thread, so values are consistent with the game
  state at call time.
- **Scope of the passthrough:** `get`/`set` cover numeric getters/setters; `setString` covers any
  single-arg setter; `call` covers no-arg commands. Methods with multiple or complex arguments aren't
  exposed so use the target peripheral directly if you need those
