export const GITHUB = "https://github.com/4everdies/CrewX";
export const RELEASES = `${GITHUB}/releases`;
export const DISCORD = "https://discord.gg/crackcrew";

export const NAV = [
  { href: "#about", label: "About" },
  { href: "#servers", label: "Servers" },
  { href: "#download", label: "Download" },
  { href: "#faq", label: "FAQ" },
];

export const SERVERS = [
  {
    name: "MushMC",
    mode: "BedWars · Skywars · Duels",
    copy: "Bypasses tuned for one of Brazil's most played anti-cheats. HG and ranked without the client giving you away.",
    img: "/images/MushMC.png",
    tag: "STABLE",
  },
  {
    name: "Hylex",
    mode: "BedWars · SkyWars · Duels",
    copy: "Routines built for Hylex pacing. Stable scaffold, clean combat and a HUD that stays out of the way.",
    img: "/images/hylex.png",
    tag: "PRACTICE",
  },
  {
    name: "Kaizen",
    mode: "BedWars · Duels",
    copy: "Focused on practice and clutch fights. Velocity and aura tuned for BR ping without ridiculous rubberbanding.",
    img: "/images/kaizen.png",
    tag: "PRIMARY",
  },
];

export const BUILD_STEPS = [
  {
    n: "01",
    title: "Clone the repository",
    code: "git clone https://github.com/4everdies/CrewX.git",
  },
  {
    n: "02",
    title: "Build with Gradle",
    code: "./gradlew build",
  },
  {
    n: "03",
    title: "Move the .jar into Forge",
    code: "build/libs/CrewX.jar",
  },
];

export const FAQS = [
  {
    q: "does it work on servers other than mushmc?",
    a: "yes. this works on mushmc, hylex and kaizen, with each bypass tuned around that server's anti-cheat.",
  },
  {
    q: "can i be banned for using it?",
    a: "third-party modifications are allowed by mojang, but every server has its own rules and some punish cheat clients hard. use it at your own risk.",
  },
  {
    q: "will crewx kill my fps?",
    a: "no. clean render and no fps stutt.",
  },
  {
    q: "what are the hotkeys?",
    a: "right shift opens the clickgui by default, and every module has its own keybind you can change in-game.",
  },
  {
    q: "is this actually safe?",
    a: "yes. crewx is fully open source. if you don't trust it, audit every line on github.",
  },
  {
    q: "how do i install it?",
    a: "just download the .jar from releases and place it in your 'mods' folder within your minecraft 1.8.9 forge profile or compatible client.",
  },
];
