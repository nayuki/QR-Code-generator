import std/os

proc target(name, profile: string, run = false, options = "") =
  var command = quoteShell(getCurrentCompilerExe()) & " c " & options &
    " --outDir:" & quoteShell(thisDir() / "build" / profile) &
    " --nimcache:" & quoteShell(thisDir() / "build" / "cache" / NimVersion / profile)
  if run: command.add " -r"
  command.add " " & quoteShell(thisDir() / (name & ".nim"))
  exec command

task build, "Build the QR Code demo":
  target("qrcodegen_demo", "debug")

task buildRelease, "Build the optimized QR Code demo":
  target("qrcodegen_demo", "release", options = "-d:release --opt:speed")

task demo, "Build and run the QR Code demo":
  target("qrcodegen_demo", "debug", run = true)

task test, "Run QR Code regression cases":
  target("qrcodegen_test", "test", run = true)

task bench, "Measure QR encoding time":
  target("qrcodegen_test", "bench", run = true,
    options = "-d:release --opt:speed -d:qrcodegenBench")

task clean, "Remove generated build output":
  let folder = normalizedPath(absolutePath(thisDir() / "build"))
  doAssert folder == normalizedPath(absolutePath(thisDir())) / "build"
  if dirExists(folder): rmDir(folder)
