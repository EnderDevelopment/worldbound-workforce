# Minecraft Worker Automation Plugin

Worldbound Workforce is a Minecraft server plugin for assigning nearby mobs automated mine, lumberjack, or digging jobs. Workers search nearby blocks for job-specific targets, break them, and drop the resulting items.

## Install

1. Use a Paper server running Minecraft 1.21.11 with Java 21.
2. Build the project with Maven to produce the plugin JAR.
3. Place the JAR in the server’s `plugins` folder and restart the server.

## Use

1. Stand within 6 blocks of an unassigned living mob.
2. Assign a job with `/worker assign mine`, `/worker assign lumberjack`, or `/worker assign dig`.
3. Use `/worker status` to see assigned workers within 16 blocks.
4. Use `/worker stop` near a worker to remove its job and restore its normal AI.
5. To try linking a chest, look at one within 6 blocks and run `/worker chest` near an assigned worker.

---

More Minecraft plugin projects by EnderDevelopment: https://github.com/EnderDevelopment

Project page: https://dash.enderdevelopment.com/community/0b49022c-a8df-47b4-a6fa-5878e8a68890?utm_source=github&utm_medium=readme&utm_campaign=auto_publish&utm_content=0b49022c-a8df-47b4-a6fa-5878e8a68890
