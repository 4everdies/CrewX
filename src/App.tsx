import { AnimatePresence, motion, useScroll, useSpring } from "framer-motion";
import { useEffect, useState } from "react";
import CurvedMarquee from "./components/originkit/CurvedMarquee";
import DynamicWeight from "./components/originkit/DynamicWeight";
import MagneticHoverButton from "./components/originkit/MagneticHoverButton";
import PixelDrift from "./components/originkit/PixelDrift";
import {
  BUILD_STEPS,
  DISCORD,
  FAQS,
  GITHUB,
  NAV,
  RELEASES,
  SERVERS,
} from "./data";

function DiscordIcon() {
  return (
    <svg width="15" height="15" viewBox="0 0 24 24" fill="currentColor" aria-hidden>
      <path d="M20.317 4.37a19.79 19.79 0 0 0-4.885-1.515.074.074 0 0 0-.079.037c-.21.375-.444.865-.608 1.25a18.27 18.27 0 0 0-5.487 0 12.64 12.64 0 0 0-.617-1.25.077.077 0 0 0-.079-.037A19.736 19.736 0 0 0 3.677 4.37a.07.07 0 0 0-.032.027C.533 9.046-.32 13.58.099 18.058a.082.082 0 0 0 .031.057 19.9 19.9 0 0 0 5.993 3.03.078.078 0 0 0 .084-.028c.462-.63.874-1.295 1.226-1.994a.076.076 0 0 0-.041-.106 13.107 13.107 0 0 1-1.872-.892.077.077 0 0 1-.008-.128c.126-.094.252-.192.372-.291a.074.074 0 0 1 .077-.01c3.928 1.793 8.18 1.793 12.062 0a.074.074 0 0 1 .078.01c.12.098.246.198.373.292a.077.077 0 0 1-.006.127 12.3 12.3 0 0 1-1.873.892.077.077 0 0 0-.041.107c.36.698.772 1.362 1.225 1.993a.076.076 0 0 0 .084.028 19.84 19.84 0 0 0 6.002-3.03.077.077 0 0 0 .032-.054c.5-5.177-.838-9.674-3.549-13.66a.061.061 0 0 0-.031-.03ZM8.02 15.33c-1.183 0-2.157-1.085-2.157-2.419 0-1.333.956-2.419 2.157-2.419 1.21 0 2.176 1.096 2.157 2.42 0 1.333-.956 2.418-2.157 2.418Zm7.975 0c-1.183 0-2.157-1.085-2.157-2.419 0-1.333.955-2.419 2.157-2.419 1.21 0 2.176 1.096 2.157 2.42 0 1.333-.946 2.418-2.157 2.418Z" />
    </svg>
  );
}

function FaqItem({ q, a }: { q: string; a: string }) {
  const [open, setOpen] = useState(false);
  return (
    <div className="px-5 py-4">
      <button
        onClick={() => setOpen((v) => !v)}
        className="flex w-full cursor-pointer items-center justify-between gap-4 text-left text-sm font-medium text-white"
      >
        {q}
        <span
          className={`text-[#EF4444] transition-transform duration-300 ease-out ${
            open ? "rotate-45" : ""
          }`}
        >
          +
        </span>
      </button>
      <AnimatePresence initial={false}>
        {open && (
          <motion.div
            initial={{ height: 0, opacity: 0 }}
            animate={{ height: "auto", opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}
            transition={{ duration: 0.38, ease: [0.22, 1, 0.36, 1] }}
            className="overflow-hidden"
          >
            <p className="mt-3 max-w-2xl text-sm leading-relaxed text-[#8f8f9e]">
              {a}
            </p>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}

type UserOS = "win" | "mac" | "linux";

function detectOS(): UserOS {
  const raw = `${typeof navigator !== "undefined" ? navigator.platform : ""} ${
    typeof navigator !== "undefined" ? navigator.userAgent : ""
  }`.toLowerCase();
  if (raw.includes("mac") || raw.includes("apple")) return "mac";
  if (raw.includes("linux") || raw.includes("x11")) return "linux";
  return "win";
}

function PlatformIcon() {
  const [os] = useState<UserOS>(detectOS);
  if (os === "win") {
    return (
      <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor" aria-hidden>
        <path d="M0 3.45 9.75 2.1v9.45H0Zm10.95-1.5L24 0v11.4H10.95ZM0 12.6h9.75v9.45L0 20.7ZM10.95 12.6H24V24l-13.05-1.8Z" />
      </svg>
    );
  }
  if (os === "mac") {
    return (
      <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor" aria-hidden>
        <path d="M12.152 6.896c-.948 0-2.415-1.078-3.96-1.04-2.04.027-3.91 1.183-4.961 3.014-2.117 3.675-.546 9.103 1.519 12.09 1.013 1.454 2.208 3.09 3.792 3.03 1.52-.065 2.09-.987 3.935-.987 1.831 0 2.35.987 3.96.948 1.637-.026 2.676-1.48 3.676-2.948 1.156-1.688 1.636-3.325 1.662-3.415-.039-.013-3.182-1.221-3.22-4.857-.026-3.04 2.48-4.494 2.597-4.559-1.429-2.09-3.623-2.324-4.39-2.376-2-.156-3.675 1.09-4.61 1.09zM15.53 3.83c.843-1.012 1.4-2.427 1.245-3.83-1.207.052-2.662.805-3.532 1.818-.78.896-1.454 2.338-1.273 3.714 1.338.104 2.715-.688 3.559-1.701" />
      </svg>
    );
  }
  return (
    <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor" aria-hidden>
      <path d="M12 2c-3.3 0-5 2.6-5 5.5 0 1.9-.6 3.6-1.6 5.2-.9 1.4-1.9 3-1.9 4.8 0 .9.7 1.5 1.6 1.5.5 0 .9-.2 1.3-.4.3.9 1 1.4 1.9 1.4h7.4c.9 0 1.6-.5 1.9-1.4.4.2.8.4 1.3.4.9 0 1.6-.6 1.6-1.5 0-1.8-1-3.4-1.9-4.8-1-1.6-1.6-3.3-1.6-5.2C17 4.6 15.3 2 12 2Zm-2.2 5.1c.5 0 .9.5.9 1.1s-.4 1.1-.9 1.1-.9-.5-.9-1.1.4-1.1.9-1.1Zm4.4 0c.5 0 .9.5.9 1.1s-.4 1.1-.9 1.1-.9-.5-.9-1.1.4-1.1.9-1.1ZM12 10.4l1.8 1.4c.3.2.2.7-.2.7h-3.2c-.4 0-.5-.5-.2-.7Z" />
    </svg>
  );
}

function GitHubIcon() {
  return (
    <svg width="15" height="15" viewBox="0 0 24 24" fill="currentColor" aria-hidden>
      <path d="M12 .5C5.73.5.75 5.48.75 11.76c0 4.97 3.22 9.18 7.69 10.66.56.1.77-.24.77-.54 0-.27-.01-1.16-.02-2.1-3.13.68-3.79-1.33-3.79-1.33-.51-1.3-1.25-1.65-1.25-1.65-1.02-.7.08-.68.08-.68 1.13.08 1.73 1.16 1.73 1.16 1 .1.77 1.77 2.72 1.26.08-.78.39-1.31.71-1.61-2.5-.28-5.13-1.25-5.13-5.56 0-1.23.44-2.23 1.16-3.02-.12-.28-.5-1.42.11-2.96 0 0 .95-.3 3.11 1.15a10.8 10.8 0 0 1 5.66 0c2.16-1.45 3.11-1.15 3.11-1.15.61 1.54.23 2.68.11 2.96.72.79 1.16 1.79 1.16 3.02 0 4.32-2.64 5.27-5.15 5.55.4.35.76 1.03.76 2.08 0 1.5-.01 2.71-.01 3.08 0 .3.2.65.78.54A11.02 11.02 0 0 0 23.25 11.76C23.25 5.48 18.27.5 12 .5Z" />
    </svg>
  );
}

export default function App() {
  const { scrollYProgress } = useScroll();
  const scaleX = useSpring(scrollYProgress, { stiffness: 120, damping: 24, mass: 0.3 });
  const [scrolled, setScrolled] = useState(false);
  const [open, setOpen] = useState(false);
  const [hidden, setHidden] = useState(false);

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 16);
    onScroll();
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => window.removeEventListener("scroll", onScroll);
  }, []);

  useEffect(() => {
    let last = window.scrollY;
    const onScroll = () => {
      const current = window.scrollY;
      setHidden(current > last && current > 100);
      last = current;
    };
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => window.removeEventListener("scroll", onScroll);
  }, []);

  useEffect(() => {
    const onClick = (e: MouseEvent) => {
      const anchor = (e.target as HTMLElement | null)?.closest?.(
        "a[href^='#']",
      );
      if (!anchor) return;
      const href = anchor.getAttribute("href");
      if (!href || href === "#") return;
      const el = document.getElementById(href.slice(1));
      if (!el) return;
      e.preventDefault();
      el.scrollIntoView({ behavior: "smooth", block: "start" });
      history.pushState(null, "", href === "#top" ? "/" : `/${href.slice(1)}`);
    };
    document.addEventListener("click", onClick);
    return () => document.removeEventListener("click", onClick);
  }, []);

  useEffect(() => {
    const id = window.location.pathname.replace(/^\/+/, "");
    if (id) {
      requestAnimationFrame(() =>
        document.getElementById(id)?.scrollIntoView({ block: "start" }),
      );
    }
  }, []);

  return (
    <div className="relative min-h-screen bg-[#07070b] text-[#f3f3f7]">
      <div className="noise" />
      <motion.div
        style={{ scaleX }}
        className="fixed top-0 left-0 right-0 z-[90] h-[2px] origin-left bg-[#EF4444]"
      />

      <header
        className={`fixed top-0 left-0 right-0 z-50 transition-all duration-300 ${
          scrolled ? "bg-[#07070b]/75 backdrop-blur-xl border-b border-white/5" : ""
        } ${hidden ? "-translate-y-full" : "translate-y-0"}`}
      >
        <div className="mx-auto flex h-16 max-w-6xl items-center justify-between px-5">
          <a href="#top" className="flex items-center gap-2.5">
            <img src="/images/logo.png" alt="" className="h-8 w-8 rounded-md" />
            <span className="font-[Syne] text-[15px] font-700 tracking-tight">
              CrewX
            </span>
          </a>
          <nav className="hidden items-center gap-8 md:flex">
            {NAV.map((item) => (
              <a
                key={item.href}
                href={item.href}
                className="text-[13px] text-[#b8b8c6] transition-colors hover:text-white"
              >
                {item.label}
              </a>
            ))}
          </nav>
          <div className="hidden items-center gap-3 md:flex">
            <MagneticHoverButton
              label="Discord"
              link={DISCORD}
              newTab
              fill="transparent"
              textColor="#ffffff"
              sweepColor="#5865F2"
              sweepTextColor="#ffffff"
              paddingX={20}
              paddingY={10}
              radius={999}
              magnet={12}
              border
              borderOptions={{ color: "rgba(255,255,255,0.18)", width: 1 }}
              font={{ fontSize: 13, fontWeight: 650, letterSpacing: "-0.01em" }}
            >
              <DiscordIcon />
              Discord
            </MagneticHoverButton>
            <MagneticHoverButton
              label="GitHub"
              link={GITHUB}
              newTab
              fill="#EF4444"
              textColor="#07070b"
              sweepColor="#ffffff"
              sweepTextColor="#07070b"
              paddingX={20}
              paddingY={10}
              radius={999}
              magnet={12}
              border={false}
              font={{ fontSize: 13, fontWeight: 650, letterSpacing: "-0.01em" }}
            >
              <GitHubIcon />
              GitHub
            </MagneticHoverButton>
          </div>
          <button
            className="md:hidden text-white/80"
            onClick={() => setOpen((v) => !v)}
            aria-label="Menu"
          >
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none">
              <path
                d={open ? "M6 6l12 12M18 6L6 18" : "M4 7h16M4 12h16M4 17h16"}
                stroke="currentColor"
                strokeWidth="1.8"
                strokeLinecap="round"
              />
            </svg>
          </button>
        </div>
        {open && (
          <div className="border-t border-white/5 bg-[#07070b]/95 px-5 py-4 md:hidden">
            {NAV.map((item) => (
              <a
                key={item.href}
                href={item.href}
                onClick={() => setOpen(false)}
                className="block py-2 text-sm text-[#c8c8d4]"
              >
                {item.label}
              </a>
            ))}
            <a
              href={DISCORD}
              target="_blank"
              rel="noreferrer"
              className="block py-2 text-sm text-[#c8c8d4]"
            >
              Discord
            </a>
          </div>
        )}
      </header>

      <main id="top">
        <section className="relative isolate min-h-[100svh] overflow-hidden pt-16">
          <div className="absolute inset-0 bg-gradient-to-b from-[#07070b]/55 via-[#07070b]/70 to-[#07070b]" />
          <div className="absolute inset-0 grid-fade" />
          <div className="pointer-events-none absolute -left-24 top-24 h-72 w-72 rounded-full bg-[#EF4444]/25 glow-orb" />
          <div className="pointer-events-none absolute right-0 bottom-24 h-80 w-80 rounded-full bg-[#B91C1C]/20 glow-orb" />

          <div className="relative mx-auto flex min-h-[calc(100svh-4rem)] max-w-6xl flex-col justify-center px-5 pb-24 pt-10">
            <div className="h-[200px] w-full sm:h-[240px] md:h-[300px] lg:h-[340px]">
              <PixelDrift
                text="CREWX"
                colors={["#FFFFFF", "#EF4444", "#fecaca"]}
                mode="onEnter"
                replay={false}
                particleSize={14}
                particleCount={50}
                mouseEnabled
                mouseRadius={90}
                mouseForce={28}
                fontSize={180}
                autoFit
              />
            </div>

            <motion.p
              initial={{ opacity: 0, y: 16 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: 0.35, duration: 0.7 }}
              className="mt-2 max-w-xl text-lg text-[#c8c8d6] md:text-xl"
            >
              the best cheating experience for minecraft rn.
              <span className="mt-2 block text-[15px] text-[#8b8b9c]">
                A 1.8.9 Forge client built for MushMC, Hylex and Kaizen.
                Real bypasses, high performance and no FPS death in clutch moments.
              </span>
            </motion.p>

            <motion.div
              initial={{ opacity: 0, y: 16 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: 0.5, duration: 0.7 }}
              className="mt-8 flex flex-wrap items-center gap-4"
            >
              <MagneticHoverButton
                label="Download Client"
                link={RELEASES}
                newTab
                fill="#EF4444"
                textColor="#07070b"
                sweepColor="#ffffff"
                sweepTextColor="#07070b"
                paddingX={32}
                paddingY={16}
                radius={999}
                magnet={14}
                border={false}
                font={{ fontSize: 15, fontWeight: 700, letterSpacing: "-0.02em" }}
              >
                Download Client <PlatformIcon />
              </MagneticHoverButton>
              <MagneticHoverButton
                label="View code"
                link={GITHUB}
                newTab
                fill="transparent"
                textColor="#ffffff"
                sweepColor="#EF4444"
                sweepTextColor="#07070b"
                paddingX={28}
                paddingY={16}
                radius={999}
                magnet={12}
                border
                borderOptions={{ color: "rgba(255,255,255,0.18)", width: 1 }}
                font={{ fontSize: 15, fontWeight: 600 }}
              >
                <GitHubIcon /> View code
              </MagneticHoverButton>
            </motion.div>

            <div className="mt-8 overflow-hidden">
              <CurvedMarquee
                text="CrewX is a client focused on Brazilian servers, created by the community for the community."
                color="#EF4444"
                direction="left"
                baseVelocity={24}
                curveAmount={-75}
                gap={30}
                fade
                fadePercent={14}
                font={{
                  fontFamily: "Syne, Inter, sans-serif",
                  fontWeight: 700,
                  fontSize: 42,
                  letterSpacing: "0.04em",
                }}
              />
            </div>
          </div>
        </section>

        <section id="about" className="relative mx-auto max-w-6xl px-5 py-24">
          <div className="mb-4 text-[11px] uppercase tracking-[0.22em] text-[#EF4444]">
            what is this
          </div>
          <DynamicWeight
            label="OWN BRAZIL"
            fromWeight={300}
            toWeight={900}
            strength={32}
            fontSize={72}
            color="#FFFFFF"
            align="left"
            className="max-w-4xl dw-scale"
          />
          <p className="mt-6 max-w-2xl text-[17px] leading-relaxed text-[#b4b4c2]">
            CrewX is a Minecraft client built to{" "}
            <span className="text-white">own Brazilian servers</span> like
            MushMC, Hylex and Kaizen. We focused on bypasses that actually work
            and high performance, so your game does not lag while you play.
          </p>

          <div className="mt-16 overflow-hidden rounded-3xl border border-white/8">
            <div className="grid md:grid-cols-2">
              <img
                src="/images/clickgui.png"
                alt="CrewX ClickGUI"
                className="h-full min-h-[280px] w-full object-cover"
              />
              <div className="flex flex-col justify-center bg-[#0c0c12] p-8 md:p-12">
                <div className="text-[11px] uppercase tracking-[0.2em] text-[#EF4444]">
                  clickgui
                </div>
                <h3 className="mt-3 font-[Syne] text-3xl font-700 tracking-tight">
                  Clean overlay.
                  <br />
                  Fast config.
                </h3>
                <p className="mt-4 max-w-xl text-sm leading-relaxed text-[#9a9aa8]">
                  Glass panels, obvious binds and modules separated by category.
                  Join the server, tweak fast and play without a heavy menu eating
                  frames.
                </p>
              </div>
            </div>
          </div>
        </section>

        <section id="servers" className="border-y border-white/5 bg-[#0a0a10] py-24">
          <div className="mx-auto max-w-6xl px-5">
            <div className="mb-3 text-[11px] uppercase tracking-[0.22em] text-[#EF4444]">
              targets
            </div>
            <DynamicWeight
              label="BRAZILIAN SERVERS"
              fromWeight={300}
              toWeight={900}
              strength={30}
              fontSize={56}
              color="#FFFFFF"
              align="left"
              className="dw-scale"
            />
            <p className="mt-4 max-w-xl text-sm text-[#8f8f9e]">
              This is not a generic client. Every bypass was iterated around real BR servers.
            </p>

            <div className="mt-12 grid gap-5 lg:grid-cols-3">
              {SERVERS.map((s) => (
                <article
                  key={s.name}
                  className="group overflow-hidden rounded-2xl border border-white/8 bg-[#101018]"
                >
                  <div className="relative h-48 overflow-hidden">
                    <img
                      src={s.img}
                      alt={s.name}
                      className="h-full w-full object-cover transition duration-700 group-hover:scale-105"
                    />
                    <div className="absolute inset-0 bg-gradient-to-t from-[#101018] to-transparent" />
                    <span className="absolute left-4 top-4 rounded-full bg-black/50 px-2.5 py-1 text-[10px] uppercase tracking-[0.16em] text-[#fecaca] backdrop-blur">
                      {s.tag}
                    </span>
                  </div>
                  <div className="p-6">
                    <h3 className="font-[Syne] text-2xl font-700">{s.name}</h3>
                    <div className="mt-1 text-xs uppercase tracking-[0.14em] text-[#EF4444]">
                      {s.mode}
                    </div>
                    <p className="mt-3 text-sm leading-relaxed text-[#9a9aa8]">{s.copy}</p>
                  </div>
                </article>
              ))}
            </div>
          </div>
        </section>

        <section id="download" className="mx-auto max-w-6xl px-5 py-24">
          <div className="grid items-center gap-12 lg:grid-cols-2">
            <div>
              <div className="mb-3 text-[11px] uppercase tracking-[0.22em] text-[#EF4444]">
                download
              </div>
              <h2 className="font-[Syne] text-4xl font-700 tracking-tight md:text-5xl">
                Download.
                <br />
                Run it.
              </h2>
              <p className="mt-4 text-sm leading-relaxed text-[#8f8f9e]">
                Grab the latest jar from GitHub Releases and drop it into your
                mods folder. No closed launcher, no key system.
              </p>
              <div className="mt-8 flex flex-wrap gap-3">
                <MagneticHoverButton
                  label="Open Source"
                  link={GITHUB}
                  newTab
                  fill="#EF4444"
                  textColor="#07070b"
                  sweepColor="#ffffff"
                  sweepTextColor="#07070b"
                  paddingX={28}
                  paddingY={14}
                  radius={999}
                  magnet={14}
                  border={false}
                  font={{ fontSize: 14, fontWeight: 700 }}
                >
                  <GitHubIcon /> Open Source
                </MagneticHoverButton>
                <MagneticHoverButton
                  label="Star the repo"
                  link={`${GITHUB}/stargazers`}
                  newTab
                  fill="transparent"
                  textColor="#ffffff"
                  sweepColor="#EF4444"
                  sweepTextColor="#07070b"
                  paddingX={24}
                  paddingY={14}
                  radius={999}
                  magnet={12}
                  border
                  borderOptions={{ color: "rgba(255,255,255,0.16)", width: 1 }}
                  font={{ fontSize: 14, fontWeight: 600 }}
                >
                  drop a star
                </MagneticHoverButton>
              </div>
            </div>

            <div>
              <div className="mb-4 text-[11px] uppercase tracking-[0.22em] text-[#6d6d7c]">
                or build from source
              </div>
              <ol className="space-y-3">
                {BUILD_STEPS.map((s) => (
                  <li
                    key={s.n}
                    className="rounded-2xl border border-white/8 bg-[#101018] p-5"
                  >
                    <div className="mb-2 flex items-center gap-3">
                      <span className="font-mono text-xs text-[#EF4444]">{s.n}</span>
                      <span className="text-sm font-medium">{s.title}</span>
                    </div>
                    <code className="block overflow-x-auto rounded-lg bg-black/40 px-3 py-2 font-mono text-[12px] text-[#fecaca]">
                      {s.code}
                    </code>
                  </li>
                ))}
              </ol>
            </div>
          </div>
        </section>

        <section id="faq" className="border-t border-white/5 bg-[#0a0a10] py-20">
          <div className="mx-auto max-w-3xl px-5">
            <div className="mb-3 text-[11px] uppercase tracking-[0.22em] text-[#EF4444]">
              FAQ
            </div>
            <h2 className="font-[Syne] text-2xl font-700 tracking-tight">
              Quick answers
            </h2>
            <div className="mt-6 divide-y divide-white/5 overflow-hidden rounded-2xl border border-white/10 bg-[#101018]">
              {FAQS.map((item) => (
                <FaqItem key={item.q} q={item.q} a={item.a} />
              ))}
            </div>
          </div>
        </section>

        <section className="border-t border-white/5 bg-[#0a0a10] py-20">
          <div className="mx-auto max-w-3xl px-5">
            <div className="mb-3 text-[11px] uppercase tracking-[0.22em] text-[#EF4444]">
              legal stuff
            </div>
            <h2 className="font-[Syne] text-2xl font-700 tracking-tight">
              Legal notice
            </h2>
            <p className="mt-4 text-sm leading-relaxed text-[#8f8f9e]">
              According to the official{" "}
              <a
                href="https://help.minecraft.net/hc/en-us/articles/4409139065613-Mods-for-Minecraft-Java-Edition"
                target="_blank"
                rel="noreferrer"
                className="text-[#fca5a5] underline decoration-white/20 underline-offset-2 hover:text-white"
              >
                Minecraft guidelines
              </a>
              , players are allowed to modify the game with mods. This project is
              a modification for Java Edition. Use it at your own risk on public
              servers. CrewX is not responsible for bans, punishments or lost accounts.
            </p>
          </div>
        </section>
      </main>

      <footer className="border-t border-white/5">
        <div className="mx-auto flex max-w-6xl flex-col items-start justify-between gap-6 px-5 py-10 md:flex-row md:items-center">
          <div className="flex items-center gap-2.5">
            <img src="/images/logo.png" alt="" className="h-7 w-7 rounded-md" />
            <div>
              <div className="font-[Syne] text-sm font-700">CrewX</div>
              <div className="text-[11px] text-[#6d6d7c]">
                1.8.9 Forge · open source
              </div>
            </div>
          </div>
          <p className="text-xs text-[#6d6d7c]">
            if you fw the project make sure to drop a star
          </p>
          <div className="flex items-center gap-5">
            <a
              href={DISCORD}
              target="_blank"
              rel="noreferrer"
              className="text-xs text-[#fca5a5] hover:text-white"
            >
              discord.gg/crackcrew
            </a>
            <a
              href={GITHUB}
              target="_blank"
              rel="noreferrer"
              className="text-xs text-[#fca5a5] hover:text-white"
            >
              github.com/4everdies/CrewX
            </a>
          </div>
        </div>
      </footer>
    </div>
  );
}
