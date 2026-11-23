#!/usr/bin/env python3
"""Patch Meta's official XrInput sample into the first VRFlex OpenXR menu prototype.

Run from the repository root:
  python3 VRFlex/OpenXR/patch_sample.py /path/to/Meta-OpenXR-SDK
"""
from pathlib import Path
import re
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_sample.py META_OPENXR_SDK_DIR")

root = Path(sys.argv[1])
source = root / "Samples/XrSamples/XrInput/Src/main.cpp"
if not source.is_file():
    raise SystemExit(f"Meta OpenXR sample not found: {source}")

s = source.read_text(encoding="utf-8")

def replace_once(old: str, new: str, label: str) -> None:
    global s
    count = s.count(old)
    if count != 1:
        raise SystemExit(f"Expected one match for {label}, found {count}")
    s = s.replace(old, new, 1)

replace_once(
'''        actionSelect_ = CreateAction(
            actionSetMenu_,
            XR_ACTION_TYPE_BOOLEAN_INPUT,
            "select",
            "Select/Click UI Element", // Displayed to users, should be translated to the user's
                                       // local language
            2,
            bothHands);
''',
'''        actionSelect_ = CreateAction(
            actionSetMenu_,
            XR_ACTION_TYPE_BOOLEAN_INPUT,
            "select",
            "Select/Click UI Element",
            2,
            bothHands);

        // Hold the left Touch controller Y button to show the VRFlex wrist menu.
        actionMenuToggle_ = CreateAction(
            actionSetMenu_,
            XR_ACTION_TYPE_BOOLEAN_INPUT,
            "toggle_menu",
            "Hold Y to show VRFlex menu",
            1,
            &leftHandPath_);
''',
"left Y action")

replace_once(
'''            {actionSelect_,             "/user/hand/left/input/trigger/value"},
            {actionSpawnCube_,          "/user/hand/left/input/trigger/value"},''',
'''            {actionSelect_,             "/user/hand/left/input/trigger/value"},
            {actionMenuToggle_,         "/user/hand/left/input/y/click"},
            {actionSpawnCube_,          "/user/hand/left/input/trigger/value"},''',
"Touch Y binding")
replace_once(
'''            {actionSelect_,             "/user/hand/left/input/trigger/value"},
            {actionSpawnCube_,          "/user/hand/left/input/trigger/value"},''',
'''            {actionSelect_,             "/user/hand/left/input/trigger/value"},
            {actionMenuToggle_,         "/user/hand/left/input/y/click"},
            {actionSpawnCube_,          "/user/hand/left/input/trigger/value"},''',
"Touch Pro Y binding")

replace_once(
'''        OXR(xrLocateSpace(spaceGripRight_, mainReferenceSpace_, time, &locationGripRight_));
        OXR(xrLocateSpace(spaceGripLeft_, mainReferenceSpace_, time, &locationGripLeft_));''',
'''        OXR(xrLocateSpace(spaceGripRight_, mainReferenceSpace_, time, &locationGripRight_));
        OXR(xrLocateSpace(spaceGripLeft_, mainReferenceSpace_, time, &locationGripLeft_));

        // This action belongs to VRFlex's own OpenXR session. The menu is visible
        // only while Y is held and follows the tracked left controller grip pose.
        UpdateVrFlexMenu();''',
"per-frame menu update")

replace_once(
'''        CreateSampleDescriptionPanel();
        SetupActionUIPanels();
        SetupMenuPanels();''',
'''        SetupMenuPanels();''',
"remove sample panels")

# Replace the sample's large world-fixed menu with a small controller-attached VRFlex panel.
pattern = re.compile(r'    void SetupMenuPanels\\(\\) \\{.*?\\n    \\}\\n\\n    void CreateSampleDescriptionPanel\\(\\) \\{.*?\\n    \\}', re.S)
new_methods = '''    void SetupMenuPanels() {
        menuTitle_ = ui_.AddLabel("VRFLEX", {0.0_m, 0.0_m, -0.15_m}, {620.0f, 70.0f});
        menuBody_ = ui_.AddLabel(
            "ROOTLESS QUEST DEVELOPER MENU\\\\nChoose a section with the right controller trigger.",
            {0.0_m, 0.0_m, -0.15_m},
            {620.0f, 160.0f});
        menuMovement_ = ui_.AddButton("MOVEMENT", {0.0_m, 0.0_m, -0.15_m}, {620.0f, 58.0f},
            [this]() { menuBody_->SetText("MOVEMENT\\\\nSeated comfort and app-supported movement settings."); });
        menuControllers_ = ui_.AddButton("CONTROLLERS", {0.0_m, 0.0_m, -0.15_m}, {620.0f, 58.0f},
            [this]() { menuBody_->SetText("CONTROLLERS\\\\nController pose tools for VRFlex's own OpenXR session."); });
        menuProfiles_ = ui_.AddButton("GAME PROFILES", {0.0_m, 0.0_m, -0.15_m}, {620.0f, 58.0f},
            [this]() { menuBody_->SetText("GAME PROFILES\\\\nLocal per-game profiles. Live changes need a compatible game integration."); });
        menuSettings_ = ui_.AddButton("SETTINGS", {0.0_m, 0.0_m, -0.15_m}, {620.0f, 58.0f},
            [this]() { menuBody_->SetText("SETTINGS\\\\nVRFlex runs without root, phone pairing, or system-file changes."); });

        menuTitle_->SetVisible(false);
        menuBody_->SetVisible(false);
        menuMovement_->SetVisible(false);
        menuControllers_->SetVisible(false);
        menuProfiles_->SetVisible(false);
        menuSettings_->SetVisible(false);
    }

    void CreateSampleDescriptionPanel() {}
'''
s, n = pattern.subn(new_methods, s, count=1)
if n != 1:
    raise SystemExit(f"Expected one menu panel section, found {n}")

# Move the menu elements relative to the left controller grip every frame.
anchor = '''    // Utility function to split out the UI updates
    void UpdateUI(const OVRFW::ovrApplFrameIn& in) {'''
insert = '''    void UpdateVrFlexMenu() {
        const bool tracked =
            (locationGripLeft_.locationFlags & XR_SPACE_LOCATION_POSITION_VALID_BIT) != 0 &&
            (locationGripLeft_.locationFlags & XR_SPACE_LOCATION_ORIENTATION_VALID_BIT) != 0;
        const bool show = tracked &&
            GetActionStateBoolean(actionMenuToggle_, leftHandPath_).currentState;

        VRMenuObject* items[] = {
            menuTitle_, menuBody_, menuMovement_, menuControllers_, menuProfiles_, menuSettings_};
        for (VRMenuObject* item : items) {
            if (item != nullptr) item->SetVisible(show);
        }
        if (!show) return;

        const OVR::Posef handPose = FromXrPosef(locationGripLeft_.pose);
        const auto place = [&handPose](VRMenuObject* item, float y, float z) {
            const OVR::Vector3f offset = handPose.Rotation * OVR::Vector3f(0.0f, y, z);
            item->SetLocalPosition(handPose.Translation + offset);
            item->SetLocalRotation(handPose.Rotation);
        };

        // A compact panel sits just above and in front of the left controller.
        place(menuTitle_,       0.13f, -0.20f);
        place(menuBody_,        0.02f, -0.20f);
        place(menuMovement_,   -0.09f, -0.20f);
        place(menuControllers_,-0.17f, -0.20f);
        place(menuProfiles_,   -0.25f, -0.20f);
        place(menuSettings_,   -0.33f, -0.20f);
    }

    // Utility function to split out the UI updates
    void UpdateUI(const OVRFW::ovrApplFrameIn& in) {'''
replace_once(anchor, insert, "menu pose helper")

# Replace sample-specific labels with a simple UI update; menu items are built above.
pattern = re.compile(r'    void UpdateUI\\(const OVRFW::ovrApplFrameIn& in\\) \\{.*?\\n    \\}\\n\\n    // Called by the XrApp framework', re.S)
new_update = '''    void UpdateUI(const OVRFW::ovrApplFrameIn& in) {
        ui_.Update(in);
    }

    // Called by the XrApp framework'''
s, n = pattern.subn(new_update, s, count=1)
if n != 1:
    raise SystemExit(f"Expected one UpdateUI body, found {n}")

# Remove the sample's cube/tool world render; keep UI, controllers, and pointer beam.
for line in [
    "        toolRenderer_.Render(out.Surfaces);\\n",
    "        cubeRenderer_.Render(out.Surfaces);\\n",
    "        templateCubeRenderer_.Render(out.Surfaces);\\n",
]:
    if line not in s:
        raise SystemExit(f"Missing render line: {line.strip()}")
    s = s.replace(line, "", 1)

replace_once(
'''    OVRFW::TinyUI ui_;

    OVRFW::ControllerRenderer controllerRenderL_;''',
'''    OVRFW::TinyUI ui_;
    VRMenuObject* menuTitle_{nullptr};
    VRMenuObject* menuBody_{nullptr};
    VRMenuObject* menuMovement_{nullptr};
    VRMenuObject* menuControllers_{nullptr};
    VRMenuObject* menuProfiles_{nullptr};
    VRMenuObject* menuSettings_{nullptr};

    OVRFW::ControllerRenderer controllerRenderL_;''',
"menu UI members")
replace_once(
'''    XrAction actionSelect_{XR_NULL_HANDLE};
    XrAction actionMenuBeamPose_{XR_NULL_HANDLE};''',
'''    XrAction actionSelect_{XR_NULL_HANDLE};
    XrAction actionMenuToggle_{XR_NULL_HANDLE};
    XrAction actionMenuBeamPose_{XR_NULL_HANDLE};''',
"Y action member")

s = s.replace("XrInput Sample", "VRFlex")
s = s.replace("XrInput sample", "VRFlex")
source.write_text(s, encoding="utf-8")
print(f"Patched {source}")
