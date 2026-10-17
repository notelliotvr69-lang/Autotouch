# TWIN REQUIEM Runtime — integration status

This directory is reserved for the game integration layer. The current TWIN REQUIEM mechanics DLL is not yet connected to the actual ULTRAKILL weapon-selection and firing lifecycle. Do not ship a placeholder under the name TwinRequiem.Runtime.dll: it would misleadingly appear to be an operational runtime.

Next implementation step: inspect the pinned Assembly-CSharp reference assembly for weapon switch/input/fire/projectile methods, then implement and test the game hooks before publishing a runtime artifact.