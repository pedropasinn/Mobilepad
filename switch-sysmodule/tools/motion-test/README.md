# Motion test

A small homebrew app (`make` → `Mobilepad-MotionTest.nro`) that shows what games read from the
motion sensor of players 1-4 (as Pro Controller) and of handheld mode: acceleration, angular
velocity, angle and their peaks. It checks that the Mobilepad virtual controller delivers its
six-axis data. Copy the `.nro` to `sdmc:/switch/` and launch it from the homebrew menu; `-` resets
the peaks, `+` exits.
