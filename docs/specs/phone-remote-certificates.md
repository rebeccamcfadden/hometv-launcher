# Phone remote: a real certificate per TV (plan, not built)

**Status:** a plan for later (F44; the project lead, 2026-11-03: "plan it, build later"). Until it is built, the phone
remote keeps the TV's own certificate, and each phone sees the browser's warning once ([phone-remote.md](../phone-remote.md)).

This page says how the warning could go away the way Plex does it: the project owns a domain, each TV gets a name in
it that points at its home address, and a small service gets each TV a real certificate for that name. It also says
what that costs, what the project must be trusted with, what the service would learn, and what is still to decide.
Nothing here is built.

`hometv.example` stands for the project's domain; `192.168.1.50` for a TV's home address.

## Why the warning is there

A browser only trusts a certificate from a public authority, and an authority only vouches for a public name. A TV at
`192.168.1.50` has neither, so today the TV makes its own certificate and the phone warns once. Chrome on Android
remembers "Proceed" for 7 days per address, so a phone used less often sees it again. The other ways round it were
weighed in F44 and are worse: installing the TV's certificate on each phone (many steps, and the TV would be trusted
for every site), plain HTTP (a lasting "Not secure" label, and typed text in the clear), or a tunnel through a project
server (every key press over the internet). Only this one gives a padlock with nothing to install on the phone.

## How it would work

1. The TV makes a second key once, in Android's Keystore, beside the one it uses today, this time with a Keystore
   attestation (step 5). The key never leaves the TV.
2. The TV's **id** is made from that key: the first 16 characters of its public key's SHA-256, in lowercase base 32
   (RFC 4648: the letters a to z and the digits 2 to 7), for example `k7qm4pxtj2ab3cde`. No account, no name, nothing
   typed in.
3. The TV's **name** is its address written with dashes, then its id: `192-168-1-50.k7qm4pxtj2ab3cde.tv.hometv.example`.
4. The project's **DNS servers** answer that name with `192.168.1.50`, by reading the name itself (as sslip.io does).
   They store no addresses: a new address is just a new name, with no update anywhere.
5. The TV asks the project's **certificate service** for a certificate for `*.k7qm4pxtj2ab3cde.tv.hometv.example`. It
   sends a certificate request signed with its key, and proof that the key lives in a real Android device's Keystore.
   The service checks both, gets the certificate from Let's Encrypt, and sends it back.
6. The TV serves the page with that certificate on a **second port** (8771), and keeps today's port (8767) with its own
   certificate. The QR opens the name on the second port:
   `https://192-168-1-50.k7qm4pxtj2ab3cde.tv.hometv.example:8771/remote#<code>`. The pane always shows the plain
   address as well, for when the name doesn't open.
7. Before the certificate runs out, the TV asks again, this time with the signed request alone (the attestation is
   checked only the first time). Whenever the name can't work, the TV goes back to the address and its own
   certificate: the warning once, as today.

## The pieces

### The domain

One domain the project owns, used for nothing else, registered for many years ahead, renewed automatically, with the
registrar's transfer lock on. TVs live under `tv.hometv.example`.

**Every TV must trust whoever controls this domain's DNS.** Whoever can change its answers (the project, someone who
breaks into the DNS servers or the registrar account, or a new owner if the domain ever lapses) can get a certificate
for any TV's name without the TV's key, answer the name with their own server, read a phone's saved pairing from the
page, then point the name back at the TV and drive it. To keep that hard:

- The registrar's lock, a registration paid years ahead, and the servers and accounts kept safe: these are what
  matter against the people above.
- The TV accepts the name in a request's Host and Origin only while it holds a valid certificate from the service, and
  only on the second port. Today's port keeps accepting only the bare address, as now.
- A CAA record that allows only the project's Let's Encrypt account, and only the DNS check
  (`issue "letsencrypt.org; accounturi=…; validationmethods=dns-01"`). It lives in the same DNS, so it doesn't stop
  anyone who can change that DNS; it stops other certificate authorities, and other Let's Encrypt accounts, issuing
  for the TVs' names.
- The domain, the DNS servers and the Let's Encrypt account are the project's most sensitive things, kept like the
  release signing key.

### Let's Encrypt's limits

Let's Encrypt gives at most **50 new certificates a week per registered domain**, shared by every TV everywhere under
`hometv.example` (renewals don't count). That is the real ceiling, and it can't be got round with the Public Suffix
List: the list turns away entries that exist for names like `1-2-3-4.foo.tld` or to get round Let's Encrypt's limits.
So:

- Before launch, ask Let's Encrypt for a higher limit for the domain (they have a form for it), sized for abuse as
  well as for real TVs.
- Make a new id cost something: the service only issues for a key whose **Keystore attestation** (the key made with
  an attestation challenge) chains to Google's attestation root, isn't on Google's revocation list, and is backed by
  hardware, so ids can't be made by the thousand on a PC. That is only a speed bump: one real Android device can
  attest as many keys as it likes, and the attestation certificates are shared by whole batches of devices on purpose,
  so the service can't count keys per device. Limits per sender address and per network do the rest.
- A TV without hardware-backed attestation (some cheap TVs and boxes) never gets a certificate: it stays on its own
  certificate and the warning, as today.
- Never order twice for one id: while a TV's certificate is still fresh, the service hands back the one it has. Let's
  Encrypt also allows only **5 certificates a week for exactly the same name**, with no way to raise it, so a TV that
  retries after a lost reply must not use them up.

### The name per TV

The address is in the name, so the DNS servers need no table and nothing to update when the TV moves. They answer only
private addresses (192.168.x.x, 10.x.x.x, 172.16.x.x to 172.31.x.x). The answer is fixed by the name, so the names can't
be pointed at anything on the internet or turned against someone else's network. The certificate is a wildcard for the
TV's id, so one certificate covers every address the TV ever has.

There are two DNS servers, on two hosts, both running the project's own program: a ready-made "secondary DNS" service
can't serve these names, because it copies stored records and these answers are worked out from the name. Let's
Encrypt checks the TXT record from several places at once, so both servers must have it.

### Getting and renewing each TV's certificate

The service does the Let's Encrypt part (ACME, with the DNS-01 check that wildcards need): it holds the project's Let's
Encrypt account, puts the check's TXT record under `_acme-challenge.k7qm4pxtj2ab3cde.tv.hometv.example` on both DNS
servers, and returns the certificate. The TV only builds a certificate request and makes one HTTPS call, which keeps
the APK small (no ACME client on the TV).

The service issues only when all of these hold:

1. The request's signature is good.
2. The id is the first 16 characters of the base-32 SHA-256 of **the request's own** public key.
3. The request names exactly `*.<that id>.tv.hometv.example`, and nothing else.
4. The signed request carries a fresh time and a random number the service hasn't seen (the attestation can't: its
   challenge is fixed when the key is made).
5. The first time an id asks (**enrolment**): the attestation is for the request's key and passes the checks under
   Let's Encrypt's limits below. The service then writes the id down, with the date.
6. Later (**renewal**): the id is one the service enrolled. No attestation: the signed request is the proof, since only
   the key behind the id can sign it.

With those checks a copied request, or a request with someone else's public key, only gets a certificate that the
real TV alone can use.

Attestation is only checked at enrolment because it can't be renewed. It is made when the key is made, and on newer
devices (Remote Key Provisioning) its certificates are short-lived, so a chain checked months later would fail even
though the key still works. If the service ever loses its list of enrolled ids, or a TV's enrolment is refused, the
TV makes a new key with a fresh attestation, and so a new id and name: phones that used the old name pair again on
the new one (see "When the name doesn't open"). The service's only table is that list: id, public key, enrolment
date, last renewal.

Certificates get shorter: Let's Encrypt goes from 90 days to 64, then 45 days by 2028, and a check of a name then lasts
only 7 hours. So the TV renews about every 30 days, starting at two thirds of the certificate's life, and tries again
each day until it works. A few days with the service down cost nothing; renewals just wait.

Later, Let's Encrypt's planned DNS-PERSIST-01 check (a fixed TXT record per TV) could let a TV renew with Let's Encrypt
itself, with the service needed only once. Worth a look when it ships.

### The key stays on the TV

Plex makes each server's key on its own servers and sends it down. This plan doesn't: the TV's key is made in the
Keystore, can't be copied out, and signs the certificate request itself, as today's key signs the TV's own
certificate. It is a second key, under its own name, because today's was made without an attestation challenge and
can't be attested later. The service sees only the public key and the request, both public anyway. A TV that is reset or uninstalled
makes a new key, so it gets a new id and name.

### Which certificate a phone gets: a second port

The obvious way, one port that picks the certificate by the name the phone asks for (TLS's SNI), doesn't work on the
TVs this launcher runs on: Android 8 to 10's TLS library picks the server's certificate before it reads the phone's
first message, so the name isn't known yet (picking after it arrived in Android 11). So the real certificate gets its
own port with its own TLS setup, and the QR names the port anyway. It can't be 8768: today's remote already moves
to the next free port from 8767 up to 8770 when one is taken, and remembers it. So the second port is 8771, or the
next free one up to 8774, remembered the same way so the name saved on phones keeps working. Port 8767 stays as today, with the TV's own
certificate. (Today's `RemoteTls` keeps the chosen certificate in one shared field; two certificates need two separate
setups, not that field.)

### When the name doesn't open

The name fails to open in several cases, and then the remote doesn't open at all, which is worse than a warning:

- Many home routers refuse DNS answers that point at a home address ("DNS rebinding protection"): the Fritz!Box,
  common in Sweden, pfSense, OpenWrt.
- So do some DNS services a phone may use on its own, through Android's Private DNS or the browser's secure DNS:
  NextDNS's rebinding protection, OpenDNS's "block internal IP addresses".
- The phone may ask a different DNS server than the TV (one the router hands out over IPv6, for example), or the
  project's DNS servers may be down.

So **each time the pane shows the QR**, the TV checks three things, and the QR opens the name only when all three
hold:

1. It has a certificate from the service that is valid now (and not about to run out).
2. Its second port is open, with that certificate.
3. Its own name, looked up through the router, answers its address.

Otherwise the QR opens the address (today's port, its own certificate, the warning once), and the pane says which
one failed, because each has its own fix:

- No certificate yet, or it ran out: "No-warning address not ready: the HomeTV service can't be reached. Phones see a
  warning once." (The TV keeps trying each day.)
- The second port couldn't open: "No-warning address not available: another app holds its port."
- The name answers something else, or nothing: "Your router blocks the no-warning address. Phones see a warning once."
  The setup page names the router settings that let it through: a Fritz!Box rebind exception for `tv.hometv.example`,
  dnsmasq's `rebind-domain-ok`, pfSense's private-domain setting.

When all three pass, the pane still shows the plain address under the QR: "If it doesn't open on a phone, use this
instead."

The name and the plain address are two different sites to the phone's browser, and the browser keeps the phone's
pairing per site. So a phone that paired on the name and then has to use the address (new router, the TV's
certificate ran out, the project's DNS gone) **pairs again** there with the code (the QR carries it, as now), and
saves the address to its home screen in place of the old shortcut, which keeps opening the name and failing. The
pane's lines and docs/phone-remote.md say so. Going back to the name later is the same: pair once more there.

## What the project must run and pay for

| What | Cost |
|---|---|
| The domain, registered years ahead | about 10 to 15 USD a year |
| Two small servers on two hosts: each runs the DNS server, one also the certificate service | about 4 to 6 USD a month each |
| Someone who keeps it running: the domain, the servers' updates, the Let's Encrypt account and its limit, a look when it fails | time, for as long as TVs use it |

About 110 to 160 USD a year. If the project ever stops: each TV's QR goes back to the address once its certificate
runs out or its name stops answering; phones that saved the name pair again on the address and save a new shortcut
(see "When the name doesn't open").

## Privacy

What the project's servers would learn:

- **Each TV's id and public key**, and what the attestation says: the device's Android version and security patch
  level, whether its bootloader is unlocked, and the app's package name and signing certificate.
- **The house's public internet address**, each time the TV asks for a certificate (about once a month), linked to the
  id. A public address is personal data (in the EU, under the GDPR); it gives a rough location. The privacy note says
  why it is kept and for how long.
- **The TV's home address and when it is used**, from DNS: each time a phone opens the name, and each time the TV checks
  its own name (whenever the pane shows the QR), the household's DNS server asks the project's DNS servers for
  `192-168-1-50.k7qm4pxtj2ab3cde…`, unless it is cached. The home address is a private one (the same in many houses),
  but together with the id it says "this TV was in use now".

Who else learns something:

- **The household's DNS provider** (the internet provider's, or Google's, Cloudflare's and so on) sees the same names,
  with the time, on every lookup.
- **The hosting companies** of the two servers can see what reaches them, whatever the project logs.
- **Everyone**, through Let's Encrypt's public Certificate Transparency logs, for good: each certificate for
  `*.k7qm4pxtj2ab3cde.tv.hometv.example`, with its date. That shows the id, not the address, and when the TV renewed,
  so roughly when it was on.

To keep that small: the servers keep no log of names asked for, keep the request log a few days at most, and the
privacy note says all of the above, the hosting companies included. The launcher's README section "What it connects
to" gets a line for it. As every new behaviour here, it is **off by default**: a second switch under Phone remote
("No warning on phones: get a certificate from the HomeTV service"), with the note beside it.

## What it changes in the launcher

- A second Keystore key with an attestation challenge, the certificate request (built like `RemoteCert`, signed by
  that key), and one call to the service.
- Keeping the certificate, renewing it, and falling back.
- A second TLS port with its own setup; Host and Origin accept the name only there, and only with a valid certificate.
- The name check each time the QR shows, the QR's link, the plain address under it, and the pane's lines.
- Unit tests for the id, the name, the request, the Host rule and the renewal timing; a TV check behind a router with
  and without rebinding protection, and a phone with Private DNS.

## Before building: to decide

1. The domain's name, and whose account registers and pays for it.
2. Who runs the two servers, and where; who looks after the domain and the Let's Encrypt account.
3. Opt-in (as above), or on whenever Phone remote is on.
4. Asking Let's Encrypt for a higher limit, and how many TVs to expect.
5. First, whether the warning is a real nuisance: how often iPhone Safari asks again, and whether Chrome's 7 days
   bother a phone used daily.

## Rough size

About 8 to 10 builder-days: the service, its DNS server and the attestation check, 3 to 4; the launcher, 4 to 5;
docs, the privacy note and the TV checks, 1. Plus setting up the two servers, and their upkeep after that.
