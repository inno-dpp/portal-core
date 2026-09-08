# Changelog

All notable changes to the DATA4CIRC Portal will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## 1.0.0 (2026-09-08)


### ✨ Features

* seed portal-core, extracted from portal-for-circularity ([61afd2e](https://github.com/inno-dpp/portal-core/commit/61afd2e88ba967de135976e541012092d18b4f4a))

## [1.1.0-beta.1](https://github.com/inno-dpp/portal-for-circularity/compare/v1.0.1-beta.2...v1.1.0-beta.1) (2026-08-31)


### ✨ Features

* **integration:** add Keycloak admin API client ([c5038a2](https://github.com/inno-dpp/portal-for-circularity/commit/c5038a2d473142262e87017d6216e2c47ebba9e9))
* **onboarding:** add Keycloak onboarding tool provisioner ([16cdcfd](https://github.com/inno-dpp/portal-for-circularity/commit/16cdcfd2d79ad8902aa7fb2d70dcb819379c0f2f))
* **onboarding:** mark optional tools as skippable on the approval page ([72d4ccb](https://github.com/inno-dpp/portal-for-circularity/commit/72d4ccbe1aebe115341535c052364825c4eac23e))
* **onboarding:** name the Keycloak group exactly after the SPIP username ([d968d74](https://github.com/inno-dpp/portal-for-circularity/commit/d968d74f7eccd75315e200f5e7d346c64105a25e))
* **onboarding:** optionally mirror first-login portal password to Keycloak ([6029f2d](https://github.com/inno-dpp/portal-for-circularity/commit/6029f2d772ea548eeedb9750617abaa16b2787d0))
* **onboarding:** support multiple admin input fields per sync tool ([3178939](https://github.com/inno-dpp/portal-for-circularity/commit/317893934cc286eb207a967e4a376c731d163779))
* **onboarding:** tolerate a disabled SPIP tool in the Keycloak provisioner ([91e9a8f](https://github.com/inno-dpp/portal-for-circularity/commit/91e9a8f57304308074bfdd5398155ee0d295392c))
* **onboarding:** use the contact email address as the Keycloak username ([a9acafb](https://github.com/inno-dpp/portal-for-circularity/commit/a9acafb2f5d40e2b8187a4b55f9f0b9850c0b2fe))
* **settings:** Keycloak default-instance platform settings panel ([c153d0b](https://github.com/inno-dpp/portal-for-circularity/commit/c153d0b1c80ae8e16e37cb2dad32347d0e91591a))


### 🐛 Bug Fixes

* add documanager client to the keycloak | add volumes and network ([787faed](https://github.com/inno-dpp/portal-for-circularity/commit/787faed12d5fb6bb912d58b2235547c0dadf7cc4))
* **dev:** disable SSL requirement in the dev Keycloak realm import ([8c21162](https://github.com/inno-dpp/portal-for-circularity/commit/8c21162d8a9349d2c493efb0403eeb8bd009f18e))
* **onboarding:** address code-review findings on the Keycloak integration ([fc50a70](https://github.com/inno-dpp/portal-for-circularity/commit/fc50a706ab8cb12815698326494439b9b023c9a6))

## [1.0.1-beta.2](https://github.com/inno-dpp/portal-for-circularity/compare/v1.0.1-beta.1...v1.0.1-beta.2) (2026-08-26)


### 🐛 Bug Fixes

* trim spip policy label to max 40 characters ([c22026e](https://github.com/inno-dpp/portal-for-circularity/commit/c22026eb51f1d0efdc55afbabc6f670565d8db28))

## [1.0.1-beta.1](https://github.com/inno-dpp/portal-for-circularity/compare/v1.0.0...v1.0.1-beta.1) (2026-08-25)


### ♻️ Code Refactoring

* remove dead JWT layer and unreachable logout handlers ([9b61bfb](https://github.com/inno-dpp/portal-for-circularity/commit/9b61bfbeacbf4abb4e3b4e47bbcfff3fe74dec7e))

## 1.0.0 (2026-08-20)


### ⚠ BREAKING CHANGES

* external SPIP clients (Documents Manager, DPP
Demonstrator) can no longer publish datasets via POST /webhook/ckan.
Dataset publication is only available through the authenticated portal
form. Existing PostgreSQL deployments retain an orphaned
ckan_dataset_requests table that can be dropped manually.

### ✨ Features

* [#218](https://github.com/inno-dpp/portal-for-circularity/issues/218) Collaboration improvement - set of the requests ([697dd6c](https://github.com/inno-dpp/portal-for-circularity/commit/697dd6cd23f43bd43ab352518d599667752263ea))
* 6.1 clarify Organizations page purpose, result count, and add collaboration entry point ([d5c7299](https://github.com/inno-dpp/portal-for-circularity/commit/d5c7299a89784182879bf77ecf9b32ec74f2fa49))
* 6.2 add NACE tooltip, clearer clear-filters action, and auto-applying search to Organizations filters ([b015bdc](https://github.com/inno-dpp/portal-for-circularity/commit/b015bdc250e4bcb2d1124c419dcecb26d609d2dd))
* 6.3 add dataset counts, collaboration status, and clearer CTAs to organization cards ([29d4750](https://github.com/inno-dpp/portal-for-circularity/commit/29d47502cf15baa015fbf9c7d835bac5eb8ff04e))
* 6.4 replace rigid pagination with progressive "Load more" loading and sticky filters ([bf94e8d](https://github.com/inno-dpp/portal-for-circularity/commit/bf94e8d5dbfcf03a7075994935efb50cf282d0ab))
* 7.1 My Organisation Page Too Administrative and Poorly Action-Oriented ([6d90f34](https://github.com/inno-dpp/portal-for-circularity/commit/6d90f3486f9959b7eb715678040fc79fc3a363a5))
* 7.2 NACE Code Warning Unclear for Non-Technical ([6122674](https://github.com/inno-dpp/portal-for-circularity/commit/612267489395825386f781c1ae5279f2362e5adb))
* 7.3 Institutional Information Without Strategic Readability ([688cd5e](https://github.com/inno-dpp/portal-for-circularity/commit/688cd5e000ebb4c80984de8d5c9bbc5e4291920e))
* 7.4 Quick Statistics with Little Operational Value ([ecb6ffe](https://github.com/inno-dpp/portal-for-circularity/commit/ecb6ffee6697e0e88f3cb431a67f8e8b8485c79d))
* 7.5 Lateral Actions Unclear and Poorly Prioritised ([05aaa81](https://github.com/inno-dpp/portal-for-circularity/commit/05aaa8116f86eccbbb65f1858e9a93d6694fc31b))
* 7.6 Members Block Without Management Actions ([1897481](https://github.com/inno-dpp/portal-for-circularity/commit/1897481ec886125ac865990f5b6b3ea77dde60b9))
* 8.1 Organisation Editing with Excess Fields and Little Clarity on What Can Be Changed ([ac23f5e](https://github.com/inno-dpp/portal-for-circularity/commit/ac23f5e7068872599f6093a4ee9de6fa76a48cf0))
* 8.2 Locked Fields Without a Clear Correction Alternative ([7a09561](https://github.com/inno-dpp/portal-for-circularity/commit/7a095613a730af843ed26970b44c5d490960909d))
* 8.3 NACE Code Selection Remains Poorly Assisted ([b011428](https://github.com/inno-dpp/portal-for-circularity/commit/b011428ab987a63bef38300fc7f0347476f8dabe))
* 8.4 Action Button Layout Poorly Balanced ([cda7e6f](https://github.com/inno-dpp/portal-for-circularity/commit/cda7e6ff0778f3ed1f4bd67b173ed50cee4835c2))
* 8.5 Member Management Mixed with Profile Editing | Improve changing the password of organization member ([aa056c8](https://github.com/inno-dpp/portal-for-circularity/commit/aa056c8be582699b736eaa26596b9e20beecc773))
* 8.8 Collaboration Requests Section Appears Empty With No Explanatory Value ([37b9e88](https://github.com/inno-dpp/portal-for-circularity/commit/37b9e88b38ce1c7f1ab416dc2e142b286e9fda8d))
* 9.2.1 Collaboration Request Email Poorly Explanatory and with Weak CTA ([35a58a5](https://github.com/inno-dpp/portal-for-circularity/commit/35a58a5d5f4c98b6d88269b16b9bd9d55728eb16))
* 9.3.1 Collaboration Notification Not Actionable and With Unclear Focus on Destination Page ([5427c5d](https://github.com/inno-dpp/portal-for-circularity/commit/5427c5d5317cd540488ecbdfcdf33c7e4541357f))
* 9.4.1 Collaboration Request Handled at the End of an Overly Long Page ([38ed7ce](https://github.com/inno-dpp/portal-for-circularity/commit/38ed7cec7230cdb816a915b7d10e82d3e187e48d))
* 9.6.1 Approval Email Confirms the Status but Does Not Explain What Changes ([7c42712](https://github.com/inno-dpp/portal-for-circularity/commit/7c427120ecf2da7d6440e8f16bfb6ef1e3111a6b))
* add clarity, trust, and contrast to the join form's ending sequence ([86cdb34](https://github.com/inno-dpp/portal-for-circularity/commit/86cdb346d8ff9b650bc86342c6dd2599fc65e824))
* add context and guidance to NACE code selection on join form ([e50c75a](https://github.com/inno-dpp/portal-for-circularity/commit/e50c75ab81eb875050ccae7c2e85b1a079ae3243))
* add legend of organization status ([f721a8e](https://github.com/inno-dpp/portal-for-circularity/commit/f721a8ed2225b98a010da506e2951722e05b31c0))
* add the filter by name and description to My Tool section ([1cb141d](https://github.com/inno-dpp/portal-for-circularity/commit/1cb141d5793556117f18448fea9ac877e3503290))
* add tooltips to the dashboard cards and quick statistics area ([28bd25a](https://github.com/inno-dpp/portal-for-circularity/commit/28bd25a80623674cfd75fb49ab86074fe2de9acb))
* admin-editable CKAN connection settings ([475462a](https://github.com/inno-dpp/portal-for-circularity/commit/475462a2b0d81ec162eef3c81e098933b25b6718))
* admin-editable SPIP platform settings ([391aede](https://github.com/inno-dpp/portal-for-circularity/commit/391aede160c5a615e2d2e74f475d4ea89028e458))
* **branding:** change the neutral default palette to corporate blue ([e834e99](https://github.com/inno-dpp/portal-for-circularity/commit/e834e998db55bb66112a521973725446fe68abcf)), closes [#1E5AA8](https://github.com/inno-dpp/portal-for-circularity/issues/1E5AA8) [#2F3E4](https://github.com/inno-dpp/portal-for-circularity/issues/2F3E4) [#A8C6E8](https://github.com/inno-dpp/portal-for-circularity/issues/A8C6E8) [#0E7490](https://github.com/inno-dpp/portal-for-circularity/issues/0E7490)
* **branding:** ship the DATA4CIRC branding bundle in-repo ([4340619](https://github.com/inno-dpp/portal-for-circularity/commit/434061929fb7bbd23464a9cde0a2e87f5cfe9d29))
* **connectors:** type the SPIP onboarding connector as SPIP_PLATFORM ([b01a63d](https://github.com/inno-dpp/portal-for-circularity/commit/b01a63d7e90cb0dd84dbe34abc0c144976ef5f6c))
* correct email deadline inconsistency and add request details, next steps, and support links to confirmation email ([508d7e0](https://github.com/inno-dpp/portal-for-circularity/commit/508d7e0874d3a876b1104bf1e8421d9c60b3cc4d))
* create cancellation of collaboration | improve organization to be nothified and redirect when there is pending collaboration request ([e7b74f6](https://github.com/inno-dpp/portal-for-circularity/commit/e7b74f620b4a350e04171f632e1d372e1e733396))
* define access rights on my tools | display spipuser on org details | validate json on backend side ([070b303](https://github.com/inno-dpp/portal-for-circularity/commit/070b30397eda8963c5dc74970dbe4b430a6ce6a9))
* list ckan group from ckan service | display organization in edit tool form ([e84ee82](https://github.com/inno-dpp/portal-for-circularity/commit/e84ee82d97fff0c057b16241bc6185108673e767))
* manage ckan group from portal | new menu for admin ([8e6ecbc](https://github.com/inno-dpp/portal-for-circularity/commit/8e6ecbcf3a445dfcd9f09137a3ddd89e6a0367ac))
* Manage rejected organizations ([07c3818](https://github.com/inno-dpp/portal-for-circularity/commit/07c381848a1630e0aae6174f231acc6154c49761))
* manage technical error message when the email exists ([99f55c7](https://github.com/inno-dpp/portal-for-circularity/commit/99f55c77b0419b667ab9758de7a7327fb7914c10))
* Member role management ([75a9358](https://github.com/inno-dpp/portal-for-circularity/commit/75a9358a505bd46c26b1a15c15ff7b00379b8261))
* move ckan categories managment into platform settings | create a backfiil script to do a sync of existing orgs | fix attach doc in the group of ckan ([9e4255c](https://github.com/inno-dpp/portal-for-circularity/commit/9e4255cd9e3d392e9162e2de05a338624b356647))
* **onboarding:** add SPIP Agent as a config-only onboarding tool ([8aa71a7](https://github.com/inno-dpp/portal-for-circularity/commit/8aa71a7749848bf42394d039bf20aaa19107033a))
* **onboarding:** generalize tool synchronization with pluggable provisioners ([3abd4a9](https://github.com/inno-dpp/portal-for-circularity/commit/3abd4a92488bf67aea74c893a44e8ea3cd0a5c43))
* **onboarding:** ship a configuration skeleton in the EDC connector template ([ad40dda](https://github.com/inno-dpp/portal-for-circularity/commit/ad40dda870b85060b9709d818c733199b62b4874))
* **onboarding:** show config-only tools as informational cards ([b09bba5](https://github.com/inno-dpp/portal-for-circularity/commit/b09bba5cf688186dae2c681846325f497ae55eab))
* **onboarding:** support config-only tools and org-admin connector editing ([8f9a37f](https://github.com/inno-dpp/portal-for-circularity/commit/8f9a37f65e3e2a091b528dfc7b37832fb7a18ee6))
* remove public CKAN webhook endpoint ([fae60f8](https://github.com/inno-dpp/portal-for-circularity/commit/fae60f8e4d712140e7869fb0ed7209d8e2350e93)), closes [#31](https://github.com/inno-dpp/portal-for-circularity/issues/31) [#31](https://github.com/inno-dpp/portal-for-circularity/issues/31)
* Request Collaboration Action Lacks Sufficient Context ([7fef27e](https://github.com/inno-dpp/portal-for-circularity/commit/7fef27ee464bda8b6d16b35ddba0af2c527f9461))
* restructure dataspace participation fields into guided checkboxes with optional details ([c5e2786](https://github.com/inno-dpp/portal-for-circularity/commit/c5e278674c7aac9506c45f4699717f92d8f7a0c8))
* Upgrade ckan token with membership user rights ([cb3ef15](https://github.com/inno-dpp/portal-for-circularity/commit/cb3ef15eefa7430193685398e3e6004ed33ea782))


### 🐛 Bug Fixes

* 5.6 connect login screen to onboarding flow with clearer copy, states, and error messages ([95c78c4](https://github.com/inno-dpp/portal-for-circularity/commit/95c78c42dd2b9da402cc847eb0c49e0e79d040de))
* add missing properties in application.yml ([a67c04f](https://github.com/inno-dpp/portal-for-circularity/commit/a67c04f38947d7aa20763ec1b8adb38d4de2d83b))
* add more explicitally onboarding reviewers ([5366b58](https://github.com/inno-dpp/portal-for-circularity/commit/5366b5872c5129ae21a9a55323bc1ad5641a97e3))
* another fix of file ([2464723](https://github.com/inno-dpp/portal-for-circularity/commit/2464723f2baa917cd60503aa642cbc4ef0cf1967))
* **ckan:** make CKAN HTTP client compatible with dev-server CKAN instances ([a1b7d75](https://github.com/inno-dpp/portal-for-circularity/commit/a1b7d757fc2f4b4e3e47646208579f97d3e28eca))
* Collaboration message is visible in the email as well ([bd08def](https://github.com/inno-dpp/portal-for-circularity/commit/bd08def7e5b0a812a0979973fbe001ff9f724790))
* Collaboration notification and message to be visible and on more convenient places ([a391484](https://github.com/inno-dpp/portal-for-circularity/commit/a3914845f3cc1cc9e61286316efa492086a53908))
* Data Tools instructions point to Technical Identifiers section above, not below ([09e0b38](https://github.com/inno-dpp/portal-for-circularity/commit/09e0b3880fe82020829f18e56af4b0ea8b807ef1))
* delete member of organization | align the size on disabled delete button to be the same as delete buttons from members ([a4d096a](https://github.com/inno-dpp/portal-for-circularity/commit/a4d096af1f7627efa205da016c64b652cfbc974d))
* derive dashboard use-case card URLs from CKAN env vars ([34fdc24](https://github.com/inno-dpp/portal-for-circularity/commit/34fdc24420f9dde4543087df868f7bef07cf9a61))
* improve contrast on button Reset My Password within the email ([e06263f](https://github.com/inno-dpp/portal-for-circularity/commit/e06263f985f97037026b5597664673c4dd148d46))
* **logging:** log all expected 4xx outcomes at WARN, not ERROR ([fbd8919](https://github.com/inno-dpp/portal-for-circularity/commit/fbd8919bc06517f0a54e44619b67ac500fdb8cfb))
* **logging:** log authorization denials at WARN, not ERROR ([4e3b426](https://github.com/inno-dpp/portal-for-circularity/commit/4e3b4261decaf0acd07ccd797f1e79cf01539fab))
* manage email error and warning message, after submission ([156664f](https://github.com/inno-dpp/portal-for-circularity/commit/156664f915af388f4a7b6987c8813ce9ff818bdc))
* members rows links to edit page only when user can edit ([ef6535f](https://github.com/inno-dpp/portal-for-circularity/commit/ef6535f031c958fce6ac5ca22d94f1014841899c))
* Notification section override search bar of organizations, within Organization menu ([fbcf6c5](https://github.com/inno-dpp/portal-for-circularity/commit/fbcf6c521307d70a7103f0aa402fa314fcc695ba))
* preserve organisation contact email on edit ([4a87611](https://github.com/inno-dpp/portal-for-circularity/commit/4a876116b3cde7ff7a8dff3a0043e8749c9ee0e2))
* prevent deleting the only active organization admin ([#216](https://github.com/inno-dpp/portal-for-circularity/issues/216)) ([0d24d45](https://github.com/inno-dpp/portal-for-circularity/commit/0d24d451f98768e902504aa30894984d1bb8fce0))
* remove dataset section from my organization page ([5114eeb](https://github.com/inno-dpp/portal-for-circularity/commit/5114eeb837d9ef43e14f58cdca641e6a8ab1f195))
* remove upload test file in CI task ([b79f28f](https://github.com/inno-dpp/portal-for-circularity/commit/b79f28fc03f8c26be7bb63673bf5fcd60a192ff8))
* remove upload test file in CI task ([6fb9cc0](https://github.com/inno-dpp/portal-for-circularity/commit/6fb9cc0beb05e433f1fc0a91d762ba9fd0f0d224))
* replace collaboration-flow alert() popups with inline feedback ([e5495e0](https://github.com/inno-dpp/portal-for-circularity/commit/e5495e063c60a8806104ca17b90a4dfccfbaabf1))
* replace copy-token failure alert() with inline icon feedback ([3f5cfef](https://github.com/inno-dpp/portal-for-circularity/commit/3f5cfefa397cea9084416da9f56bd878702954a1))
* replace load-more failure alert() with inline error message ([4a18dde](https://github.com/inno-dpp/portal-for-circularity/commit/4a18dde61caa47e1ac1d4e77c54adbfe90b9192a))
* replace remaining member-management alert() popups with toasts ([fd7f20f](https://github.com/inno-dpp/portal-for-circularity/commit/fd7f20f5539be2f2bc41492429be0a27b55d33cb))
* replace remaining registration-form alert() popups with showFormNotice ([44a8750](https://github.com/inno-dpp/portal-for-circularity/commit/44a875088fe5201a8a387e3d336ca622faa8ff18))
* Searching organizations with the space between text ([d741fac](https://github.com/inno-dpp/portal-for-circularity/commit/d741fac130caf6733a7227366fb49d2f6ec9ee02))
* **security:** fail fast on missing or weak ENCRYPTION_KEY and JWT_SECRET ([57c75db](https://github.com/inno-dpp/portal-for-circularity/commit/57c75db46074b6375807813def02b755e55b51c4))
* **security:** guard OrganizationController endpoints against unresolvable principal ([2463e56](https://github.com/inno-dpp/portal-for-circularity/commit/2463e569fc08eee7b1ff29f15163c4cd2c5f188d))
* wrong encoding in the comment of file ([c926b39](https://github.com/inno-dpp/portal-for-circularity/commit/c926b39da23dc74893302de4b9441186c0d21525))


### 📚 Documentation

* align run and deployment documentation with the three supported methods ([f8a8756](https://github.com/inno-dpp/portal-for-circularity/commit/f8a8756a7f309e50e2efcfbd2f25eb6b40e3843b))
* fix stale development setup documentation ([ad01a99](https://github.com/inno-dpp/portal-for-circularity/commit/ad01a99449ca5eedda73746f0358712986cf374f))
* fix stale directory structure in CLAUDE.md ([d658f43](https://github.com/inno-dpp/portal-for-circularity/commit/d658f432dfb9974daf100eb15951d1a19b97ed6d))
* merge DOCKER.md into DEPLOYMENT.md as the single run/deploy guide ([96fef1e](https://github.com/inno-dpp/portal-for-circularity/commit/96fef1e6b0be5778c2bb4892c9c360bf9a3ea3da))
* remove AGENTS.md, CLAUDE.md is the single source of truth ([5b2cff7](https://github.com/inno-dpp/portal-for-circularity/commit/5b2cff772f632e7d8b26eab803bc0f2fe29781ee))
* set code of conduct contact and copyright holder ([365a0f5](https://github.com/inno-dpp/portal-for-circularity/commit/365a0f599d9a362e91e8f2f3751eed2a53781f02))
* warn that SPIP_DEFAULT_ROLE must match an existing SPIP role ([f6966a0](https://github.com/inno-dpp/portal-for-circularity/commit/f6966a0ecd27cd6d188be0e18fab7b3093ac15d6))
* **white-label:** document the branding overlay directory pattern ([744fc3d](https://github.com/inno-dpp/portal-for-circularity/commit/744fc3df3c59af37b68f9c205a4ed73641fd651c))


### ♻️ Code Refactoring

* **config:** consolidate run configurations to three supported methods ([a3122de](https://github.com/inno-dpp/portal-for-circularity/commit/a3122ded0f71a3c13636bfc89a989915274277b1))
* **onboarding:** remove redundant connector-initialization mechanism ([ca63416](https://github.com/inno-dpp/portal-for-circularity/commit/ca63416b926b65e77561995876de0c5cf42bec27))
* remove dead CKAN publish overload and update docs after dataset feature removal ([3db2657](https://github.com/inno-dpp/portal-for-circularity/commit/3db2657e27f108efac483cb8349c18b79b1a785f))
* remove EDC provisioning feature ([e4ded79](https://github.com/inno-dpp/portal-for-circularity/commit/e4ded799e3ac22e49cf80cb8ff24b3cfd5acaaa0))

## [1.0.0-beta.36](https://github.com/inno-dpp/portal-for-circularity/compare/v1.0.0-beta.35...v1.0.0-beta.36) (2026-08-19)


### 🐛 Bug Fixes

* remove dataset section from my organization page ([5114eeb](https://github.com/inno-dpp/portal-for-circularity/commit/5114eeb837d9ef43e14f58cdca641e6a8ab1f195))

## [1.0.0-beta.35](https://github.com/inno-dpp/portal-for-circularity/compare/v1.0.0-beta.34...v1.0.0-beta.35) (2026-08-18)


### ✨ Features

* move ckan categories managment into platform settings | create a backfiil script to do a sync of existing orgs | fix attach doc in the group of ckan ([9e4255c](https://github.com/inno-dpp/portal-for-circularity/commit/9e4255cd9e3d392e9162e2de05a338624b356647))

## [1.0.0-beta.34](https://github.com/inno-dpp/portal-for-circularity/compare/v1.0.0-beta.33...v1.0.0-beta.34) (2026-08-18)


### ✨ Features

* manage ckan group from portal | new menu for admin ([8e6ecbc](https://github.com/inno-dpp/portal-for-circularity/commit/8e6ecbcf3a445dfcd9f09137a3ddd89e6a0367ac))

## [1.0.0-beta.33](https://github.com/inno-dpp/portal-for-circularity/compare/v1.0.0-beta.32...v1.0.0-beta.33) (2026-08-14)


### ✨ Features

* list ckan group from ckan service | display organization in edit tool form ([e84ee82](https://github.com/inno-dpp/portal-for-circularity/commit/e84ee82d97fff0c057b16241bc6185108673e767))
* Upgrade ckan token with membership user rights ([cb3ef15](https://github.com/inno-dpp/portal-for-circularity/commit/cb3ef15eefa7430193685398e3e6004ed33ea782))

## [1.0.0-beta.32](https://github.com/inno-dpp/portal-for-circularity/compare/v1.0.0-beta.31...v1.0.0-beta.32) (2026-08-13)


### ♻️ Code Refactoring

* remove dead CKAN publish overload and update docs after dataset feature removal ([3db2657](https://github.com/inno-dpp/portal-for-circularity/commit/3db2657e27f108efac483cb8349c18b79b1a785f))

## [1.0.0-beta.31](https://github.com/inno-dpp/portal-for-circularity/compare/v1.0.0-beta.30...v1.0.0-beta.31) (2026-08-11)


### 🐛 Bug Fixes

* Collaboration message is visible in the email as well ([bd08def](https://github.com/inno-dpp/portal-for-circularity/commit/bd08def7e5b0a812a0979973fbe001ff9f724790))
* Collaboration notification and message to be visible and on more convenient places ([a391484](https://github.com/inno-dpp/portal-for-circularity/commit/a3914845f3cc1cc9e61286316efa492086a53908))
* Data Tools instructions point to Technical Identifiers section above, not below ([09e0b38](https://github.com/inno-dpp/portal-for-circularity/commit/09e0b3880fe82020829f18e56af4b0ea8b807ef1))
* Notification section override search bar of organizations, within Organization menu ([fbcf6c5](https://github.com/inno-dpp/portal-for-circularity/commit/fbcf6c521307d70a7103f0aa402fa314fcc695ba))
* preserve organisation contact email on edit ([4a87611](https://github.com/inno-dpp/portal-for-circularity/commit/4a876116b3cde7ff7a8dff3a0043e8749c9ee0e2))
* replace collaboration-flow alert() popups with inline feedback ([e5495e0](https://github.com/inno-dpp/portal-for-circularity/commit/e5495e063c60a8806104ca17b90a4dfccfbaabf1))
* replace copy-token failure alert() with inline icon feedback ([3f5cfef](https://github.com/inno-dpp/portal-for-circularity/commit/3f5cfefa397cea9084416da9f56bd878702954a1))
* replace load-more failure alert() with inline error message ([4a18dde](https://github.com/inno-dpp/portal-for-circularity/commit/4a18dde61caa47e1ac1d4e77c54adbfe90b9192a))
* replace remaining member-management alert() popups with toasts ([fd7f20f](https://github.com/inno-dpp/portal-for-circularity/commit/fd7f20f5539be2f2bc41492429be0a27b55d33cb))
* replace remaining registration-form alert() popups with showFormNotice ([44a8750](https://github.com/inno-dpp/portal-for-circularity/commit/44a875088fe5201a8a387e3d336ca622faa8ff18))
* Searching organizations with the space between text ([d741fac](https://github.com/inno-dpp/portal-for-circularity/commit/d741fac130caf6733a7227366fb49d2f6ec9ee02))

## [1.0.0-beta.30](https://github.com/inno-dpp/portal-for-circularity/compare/v1.0.0-beta.29...v1.0.0-beta.30) (2026-08-07)


### ✨ Features

* 7.6 Members Block Without Management Actions ([1897481](https://github.com/inno-dpp/portal-for-circularity/commit/1897481ec886125ac865990f5b6b3ea77dde60b9))

## [1.0.0-beta.29](https://github.com/inno-dpp/portal-for-circularity/compare/v1.0.0-beta.28...v1.0.0-beta.29) (2026-08-07)


### ✨ Features

* 7.5 Lateral Actions Unclear and Poorly Prioritised ([05aaa81](https://github.com/inno-dpp/portal-for-circularity/commit/05aaa8116f86eccbbb65f1858e9a93d6694fc31b))

## [1.0.0-beta.28](https://github.com/inno-dpp/portal-for-circularity/compare/v1.0.0-beta.27...v1.0.0-beta.28) (2026-08-07)


### ✨ Features

* 7.4 Quick Statistics with Little Operational Value ([ecb6ffe](https://github.com/inno-dpp/portal-for-circularity/commit/ecb6ffee6697e0e88f3cb431a67f8e8b8485c79d))


### 🐛 Bug Fixes

* members rows links to edit page only when user can edit ([ef6535f](https://github.com/inno-dpp/portal-for-circularity/commit/ef6535f031c958fce6ac5ca22d94f1014841899c))

## [1.0.0-beta.27](https://github.com/inno-dpp/portal-for-circularity/compare/v1.0.0-beta.26...v1.0.0-beta.27) (2026-08-06)


### ✨ Features

* 7.2 NACE Code Warning Unclear for Non-Technical ([6122674](https://github.com/inno-dpp/portal-for-circularity/commit/612267489395825386f781c1ae5279f2362e5adb))

## [1.0.0-beta.26](https://github.com/inno-dpp/portal-for-circularity/compare/v1.0.0-beta.25...v1.0.0-beta.26) (2026-08-06)


### ✨ Features

* 7.3 Institutional Information Without Strategic Readability ([688cd5e](https://github.com/inno-dpp/portal-for-circularity/commit/688cd5e000ebb4c80984de8d5c9bbc5e4291920e))

## [1.0.0-beta.25](https://github.com/inno-dpp/portal-for-circularity/compare/v1.0.0-beta.24...v1.0.0-beta.25) (2026-08-06)


### ✨ Features

* 6.4 replace rigid pagination with progressive "Load more" loading and sticky filters ([bf94e8d](https://github.com/inno-dpp/portal-for-circularity/commit/bf94e8d5dbfcf03a7075994935efb50cf282d0ab))

## [1.0.0-beta.24](https://github.com/inno-dpp/portal-for-circularity/compare/v1.0.0-beta.23...v1.0.0-beta.24) (2026-08-06)


### ✨ Features

* 6.3 add dataset counts, collaboration status, and clearer CTAs to organization cards ([29d4750](https://github.com/inno-dpp/portal-for-circularity/commit/29d47502cf15baa015fbf9c7d835bac5eb8ff04e))
* 7.1 My Organisation Page Too Administrative and Poorly Action-Oriented ([6d90f34](https://github.com/inno-dpp/portal-for-circularity/commit/6d90f3486f9959b7eb715678040fc79fc3a363a5))

## [1.0.0-beta.23](https://github.com/inno-dpp/portal-for-circularity/compare/v1.0.0-beta.22...v1.0.0-beta.23) (2026-08-06)


### ✨ Features

* 6.2 add NACE tooltip, clearer clear-filters action, and auto-applying search to Organizations filters ([b015bdc](https://github.com/inno-dpp/portal-for-circularity/commit/b015bdc250e4bcb2d1124c419dcecb26d609d2dd))

## [1.0.0-beta.22](https://github.com/inno-dpp/portal-for-circularity/compare/v1.0.0-beta.21...v1.0.0-beta.22) (2026-08-05)


### ✨ Features

* 8.8 Collaboration Requests Section Appears Empty With No Explanatory Value ([37b9e88](https://github.com/inno-dpp/portal-for-circularity/commit/37b9e88b38ce1c7f1ab416dc2e142b286e9fda8d))

## [1.0.0-beta.21](https://github.com/inno-dpp/portal-for-circularity/compare/v1.0.0-beta.20...v1.0.0-beta.21) (2026-08-05)


### ✨ Features

* 8.5 Member Management Mixed with Profile Editing | Improve changing the password of organization member ([aa056c8](https://github.com/inno-dpp/portal-for-circularity/commit/aa056c8be582699b736eaa26596b9e20beecc773))

## [1.0.0-beta.20](https://github.com/inno-dpp/portal-for-circularity/compare/v1.0.0-beta.19...v1.0.0-beta.20) (2026-08-05)


### ✨ Features

* 6.1 clarify Organizations page purpose, result count, and add collaboration entry point ([d5c7299](https://github.com/inno-dpp/portal-for-circularity/commit/d5c7299a89784182879bf77ecf9b32ec74f2fa49))

## [1.0.0-beta.19](https://github.com/inno-dpp/portal-for-circularity/compare/v1.0.0-beta.18...v1.0.0-beta.19) (2026-08-05)


### 🐛 Bug Fixes

* 5.6 connect login screen to onboarding flow with clearer copy, states, and error messages ([95c78c4](https://github.com/inno-dpp/portal-for-circularity/commit/95c78c42dd2b9da402cc847eb0c49e0e79d040de))

## [1.0.0-beta.18](https://github.com/inno-dpp/portal-for-circularity/compare/v1.0.0-beta.17...v1.0.0-beta.18) (2026-08-05)


### ✨ Features

* 8.4 Action Button Layout Poorly Balanced ([cda7e6f](https://github.com/inno-dpp/portal-for-circularity/commit/cda7e6ff0778f3ed1f4bd67b173ed50cee4835c2))

## [1.0.0-beta.17](https://github.com/inno-dpp/portal-for-circularity/compare/v1.0.0-beta.16...v1.0.0-beta.17) (2026-08-05)


### ✨ Features

* 8.3 NACE Code Selection Remains Poorly Assisted ([b011428](https://github.com/inno-dpp/portal-for-circularity/commit/b011428ab987a63bef38300fc7f0347476f8dabe))

## [1.0.0-beta.16](https://github.com/inno-dpp/portal-for-circularity/compare/v1.0.0-beta.15...v1.0.0-beta.16) (2026-08-05)


### ✨ Features

* 8.2 Locked Fields Without a Clear Correction Alternative ([7a09561](https://github.com/inno-dpp/portal-for-circularity/commit/7a095613a730af843ed26970b44c5d490960909d))

## [1.0.0-beta.15](https://github.com/inno-dpp/portal-for-circularity/compare/v1.0.0-beta.14...v1.0.0-beta.15) (2026-08-05)


### ✨ Features

* correct email deadline inconsistency and add request details, next steps, and support links to confirmation email ([508d7e0](https://github.com/inno-dpp/portal-for-circularity/commit/508d7e0874d3a876b1104bf1e8421d9c60b3cc4d))

## [1.0.0-beta.14](https://github.com/inno-dpp/portal-for-circularity/compare/v1.0.0-beta.13...v1.0.0-beta.14) (2026-08-04)


### ✨ Features

* 8.1 Organisation Editing with Excess Fields and Little Clarity on What Can Be Changed ([ac23f5e](https://github.com/inno-dpp/portal-for-circularity/commit/ac23f5e7068872599f6093a4ee9de6fa76a48cf0))

## [1.0.0-beta.13](https://github.com/inno-dpp/portal-for-circularity/compare/v1.0.0-beta.12...v1.0.0-beta.13) (2026-08-04)


### ✨ Features

* 9.6.1 Approval Email Confirms the Status but Does Not Explain What Changes ([7c42712](https://github.com/inno-dpp/portal-for-circularity/commit/7c427120ecf2da7d6440e8f16bfb6ef1e3111a6b))

## [1.0.0-beta.12](https://github.com/inno-dpp/portal-for-circularity/compare/v1.0.0-beta.11...v1.0.0-beta.12) (2026-08-04)


### ✨ Features

* add clarity, trust, and contrast to the join form's ending sequence ([86cdb34](https://github.com/inno-dpp/portal-for-circularity/commit/86cdb346d8ff9b650bc86342c6dd2599fc65e824))

## [1.0.0-beta.11](https://github.com/inno-dpp/portal-for-circularity/compare/v1.0.0-beta.10...v1.0.0-beta.11) (2026-08-04)


### ✨ Features

* 9.4.1 Collaboration Request Handled at the End of an Overly Long Page ([38ed7ce](https://github.com/inno-dpp/portal-for-circularity/commit/38ed7cec7230cdb816a915b7d10e82d3e187e48d))

## [1.0.0-beta.10](https://github.com/inno-dpp/portal-for-circularity/compare/v1.0.0-beta.9...v1.0.0-beta.10) (2026-08-04)


### ✨ Features

* 9.3.1 Collaboration Notification Not Actionable and With Unclear Focus on Destination Page ([5427c5d](https://github.com/inno-dpp/portal-for-circularity/commit/5427c5d5317cd540488ecbdfcdf33c7e4541357f))

## [1.0.0-beta.9](https://github.com/inno-dpp/portal-for-circularity/compare/v1.0.0-beta.8...v1.0.0-beta.9) (2026-08-03)


### ✨ Features

* add context and guidance to NACE code selection on join form ([e50c75a](https://github.com/inno-dpp/portal-for-circularity/commit/e50c75ab81eb875050ccae7c2e85b1a079ae3243))

## [1.0.0-beta.8](https://github.com/inno-dpp/portal-for-circularity/compare/v1.0.0-beta.7...v1.0.0-beta.8) (2026-08-03)


### ✨ Features

* 9.2.1 Collaboration Request Email Poorly Explanatory and with Weak CTA ([35a58a5](https://github.com/inno-dpp/portal-for-circularity/commit/35a58a5d5f4c98b6d88269b16b9bd9d55728eb16))

## [1.0.0-beta.7](https://github.com/inno-dpp/portal-for-circularity/compare/v1.0.0-beta.6...v1.0.0-beta.7) (2026-08-03)


### ✨ Features

* restructure dataspace participation fields into guided checkboxes with optional details ([c5e2786](https://github.com/inno-dpp/portal-for-circularity/commit/c5e278674c7aac9506c45f4699717f92d8f7a0c8))

## [1.0.0-beta.6](https://github.com/inno-dpp/portal-for-circularity/compare/v1.0.0-beta.5...v1.0.0-beta.6) (2026-07-31)


### ⚠ BREAKING CHANGES

* external SPIP clients (Documents Manager, DPP
Demonstrator) can no longer publish datasets via POST /webhook/ckan.
Dataset publication is only available through the authenticated portal
form. Existing PostgreSQL deployments retain an orphaned
ckan_dataset_requests table that can be dropped manually.

### ✨ Features

* remove public CKAN webhook endpoint ([fae60f8](https://github.com/inno-dpp/portal-for-circularity/commit/fae60f8e4d712140e7869fb0ed7209d8e2350e93)), closes [#31](https://github.com/inno-dpp/portal-for-circularity/issues/31) [#31](https://github.com/inno-dpp/portal-for-circularity/issues/31)


### 🐛 Bug Fixes

* derive dashboard use-case card URLs from CKAN env vars ([34fdc24](https://github.com/inno-dpp/portal-for-circularity/commit/34fdc24420f9dde4543087df868f7bef07cf9a61))

## [1.0.0-beta.5](https://github.com/inno-dpp/portal-for-circularity/compare/v1.0.0-beta.4...v1.0.0-beta.5) (2026-07-23)


### ✨ Features

* [#218](https://github.com/inno-dpp/portal-for-circularity/issues/218) Collaboration improvement - set of the requests ([697dd6c](https://github.com/inno-dpp/portal-for-circularity/commit/697dd6cd23f43bd43ab352518d599667752263ea))
* add legend of organization status ([f721a8e](https://github.com/inno-dpp/portal-for-circularity/commit/f721a8ed2225b98a010da506e2951722e05b31c0))
* add the filter by name and description to My Tool section ([1cb141d](https://github.com/inno-dpp/portal-for-circularity/commit/1cb141d5793556117f18448fea9ac877e3503290))
* add tooltips to the dashboard cards and quick statistics area ([28bd25a](https://github.com/inno-dpp/portal-for-circularity/commit/28bd25a80623674cfd75fb49ab86074fe2de9acb))
* admin-editable CKAN connection settings ([475462a](https://github.com/inno-dpp/portal-for-circularity/commit/475462a2b0d81ec162eef3c81e098933b25b6718))
* admin-editable SPIP platform settings ([391aede](https://github.com/inno-dpp/portal-for-circularity/commit/391aede160c5a615e2d2e74f475d4ea89028e458))
* create cancellation of collaboration | improve organization to be nothified and redirect when there is pending collaboration request ([e7b74f6](https://github.com/inno-dpp/portal-for-circularity/commit/e7b74f620b4a350e04171f632e1d372e1e733396))
* define access rights on my tools | display spipuser on org details | validate json on backend side ([070b303](https://github.com/inno-dpp/portal-for-circularity/commit/070b30397eda8963c5dc74970dbe4b430a6ce6a9))
* Manage rejected organizations ([07c3818](https://github.com/inno-dpp/portal-for-circularity/commit/07c381848a1630e0aae6174f231acc6154c49761))
* manage technical error message when the email exists ([99f55c7](https://github.com/inno-dpp/portal-for-circularity/commit/99f55c77b0419b667ab9758de7a7327fb7914c10))
* Member role management ([75a9358](https://github.com/inno-dpp/portal-for-circularity/commit/75a9358a505bd46c26b1a15c15ff7b00379b8261))
* Request Collaboration Action Lacks Sufficient Context ([7fef27e](https://github.com/inno-dpp/portal-for-circularity/commit/7fef27ee464bda8b6d16b35ddba0af2c527f9461))


### 🐛 Bug Fixes

* add missing properties in application.yml ([a67c04f](https://github.com/inno-dpp/portal-for-circularity/commit/a67c04f38947d7aa20763ec1b8adb38d4de2d83b))
* add more explicitally onboarding reviewers ([5366b58](https://github.com/inno-dpp/portal-for-circularity/commit/5366b5872c5129ae21a9a55323bc1ad5641a97e3))
* another fix of file ([2464723](https://github.com/inno-dpp/portal-for-circularity/commit/2464723f2baa917cd60503aa642cbc4ef0cf1967))
* delete member of organization | align the size on disabled delete button to be the same as delete buttons from members ([a4d096a](https://github.com/inno-dpp/portal-for-circularity/commit/a4d096af1f7627efa205da016c64b652cfbc974d))
* improve contrast on button Reset My Password within the email ([e06263f](https://github.com/inno-dpp/portal-for-circularity/commit/e06263f985f97037026b5597664673c4dd148d46))
* **logging:** log all expected 4xx outcomes at WARN, not ERROR ([fbd8919](https://github.com/inno-dpp/portal-for-circularity/commit/fbd8919bc06517f0a54e44619b67ac500fdb8cfb))
* **logging:** log authorization denials at WARN, not ERROR ([4e3b426](https://github.com/inno-dpp/portal-for-circularity/commit/4e3b4261decaf0acd07ccd797f1e79cf01539fab))
* manage email error and warning message, after submission ([156664f](https://github.com/inno-dpp/portal-for-circularity/commit/156664f915af388f4a7b6987c8813ce9ff818bdc))
* prevent deleting the only active organization admin ([#216](https://github.com/inno-dpp/portal-for-circularity/issues/216)) ([0d24d45](https://github.com/inno-dpp/portal-for-circularity/commit/0d24d451f98768e902504aa30894984d1bb8fce0))
* remove upload test file in CI task ([6fb9cc0](https://github.com/inno-dpp/portal-for-circularity/commit/6fb9cc0beb05e433f1fc0a91d762ba9fd0f0d224))
* **security:** guard OrganizationController endpoints against unresolvable principal ([2463e56](https://github.com/inno-dpp/portal-for-circularity/commit/2463e569fc08eee7b1ff29f15163c4cd2c5f188d))
* wrong encoding in the comment of file ([c926b39](https://github.com/inno-dpp/portal-for-circularity/commit/c926b39da23dc74893302de4b9441186c0d21525))

## [1.0.0-beta.4](https://github.com/inno-dpp/portal-for-circularity/compare/v1.0.0-beta.3...v1.0.0-beta.4) (2026-07-15)


### 🐛 Bug Fixes

* remove upload test file in CI task ([b79f28f](https://github.com/inno-dpp/portal-for-circularity/commit/b79f28fc03f8c26be7bb63673bf5fcd60a192ff8))


### 📚 Documentation

* fix stale directory structure in CLAUDE.md ([d658f43](https://github.com/inno-dpp/portal-for-circularity/commit/d658f432dfb9974daf100eb15951d1a19b97ed6d))
* remove AGENTS.md, CLAUDE.md is the single source of truth ([5b2cff7](https://github.com/inno-dpp/portal-for-circularity/commit/5b2cff772f632e7d8b26eab803bc0f2fe29781ee))

## [1.0.0-beta.3](https://github.com/inno-dpp/portal-for-circularity/compare/v1.0.0-beta.2...v1.0.0-beta.3) (2026-07-07)


### ✨ Features

* **branding:** ship the DATA4CIRC branding bundle in-repo ([4340619](https://github.com/inno-dpp/portal-for-circularity/commit/434061929fb7bbd23464a9cde0a2e87f5cfe9d29))

## [1.0.0-beta.2](https://github.com/inno-dpp/portal-for-circularity/compare/v1.0.0-beta.1...v1.0.0-beta.2) (2026-07-06)


### ✨ Features

* **branding:** change the neutral default palette to corporate blue ([e834e99](https://github.com/inno-dpp/portal-for-circularity/commit/e834e998db55bb66112a521973725446fe68abcf)), closes [#1E5AA8](https://github.com/inno-dpp/portal-for-circularity/issues/1E5AA8) [#2F3E4](https://github.com/inno-dpp/portal-for-circularity/issues/2F3E4) [#A8C6E8](https://github.com/inno-dpp/portal-for-circularity/issues/A8C6E8) [#0E7490](https://github.com/inno-dpp/portal-for-circularity/issues/0E7490)


### 📚 Documentation

* **white-label:** document the branding overlay directory pattern ([744fc3d](https://github.com/inno-dpp/portal-for-circularity/commit/744fc3df3c59af37b68f9c205a4ed73641fd651c))

## 1.0.0-beta.1 (2026-07-06)


### ✨ Features

* **connectors:** type the SPIP onboarding connector as SPIP_PLATFORM ([b01a63d](https://github.com/inno-dpp/portal-for-circularity/commit/b01a63d7e90cb0dd84dbe34abc0c144976ef5f6c))
* **onboarding:** add SPIP Agent as a config-only onboarding tool ([8aa71a7](https://github.com/inno-dpp/portal-for-circularity/commit/8aa71a7749848bf42394d039bf20aaa19107033a))
* **onboarding:** generalize tool synchronization with pluggable provisioners ([3abd4a9](https://github.com/inno-dpp/portal-for-circularity/commit/3abd4a92488bf67aea74c893a44e8ea3cd0a5c43))
* **onboarding:** ship a configuration skeleton in the EDC connector template ([ad40dda](https://github.com/inno-dpp/portal-for-circularity/commit/ad40dda870b85060b9709d818c733199b62b4874))
* **onboarding:** show config-only tools as informational cards ([b09bba5](https://github.com/inno-dpp/portal-for-circularity/commit/b09bba5cf688186dae2c681846325f497ae55eab))
* **onboarding:** support config-only tools and org-admin connector editing ([8f9a37f](https://github.com/inno-dpp/portal-for-circularity/commit/8f9a37f65e3e2a091b528dfc7b37832fb7a18ee6))


### 🐛 Bug Fixes

* **ckan:** make CKAN HTTP client compatible with dev-server CKAN instances ([a1b7d75](https://github.com/inno-dpp/portal-for-circularity/commit/a1b7d757fc2f4b4e3e47646208579f97d3e28eca))
* **security:** fail fast on missing or weak ENCRYPTION_KEY and JWT_SECRET ([57c75db](https://github.com/inno-dpp/portal-for-circularity/commit/57c75db46074b6375807813def02b755e55b51c4))


### 📚 Documentation

* align run and deployment documentation with the three supported methods ([f8a8756](https://github.com/inno-dpp/portal-for-circularity/commit/f8a8756a7f309e50e2efcfbd2f25eb6b40e3843b))
* fix stale development setup documentation ([ad01a99](https://github.com/inno-dpp/portal-for-circularity/commit/ad01a99449ca5eedda73746f0358712986cf374f))
* merge DOCKER.md into DEPLOYMENT.md as the single run/deploy guide ([96fef1e](https://github.com/inno-dpp/portal-for-circularity/commit/96fef1e6b0be5778c2bb4892c9c360bf9a3ea3da))
* set code of conduct contact and copyright holder ([365a0f5](https://github.com/inno-dpp/portal-for-circularity/commit/365a0f599d9a362e91e8f2f3751eed2a53781f02))
* warn that SPIP_DEFAULT_ROLE must match an existing SPIP role ([f6966a0](https://github.com/inno-dpp/portal-for-circularity/commit/f6966a0ecd27cd6d188be0e18fab7b3093ac15d6))


### ♻️ Code Refactoring

* **config:** consolidate run configurations to three supported methods ([a3122de](https://github.com/inno-dpp/portal-for-circularity/commit/a3122ded0f71a3c13636bfc89a989915274277b1))
* **onboarding:** remove redundant connector-initialization mechanism ([ca63416](https://github.com/inno-dpp/portal-for-circularity/commit/ca63416b926b65e77561995876de0c5cf42bec27))
* remove EDC provisioning feature ([e4ded79](https://github.com/inno-dpp/portal-for-circularity/commit/e4ded799e3ac22e49cf80cb8ff24b3cfd5acaaa0))

# Changelog

All notable changes to this project are documented in this file. It is
generated automatically by [semantic-release](https://semantic-release.gitbook.io/)
from [Conventional Commits](https://www.conventionalcommits.org/).

The project was developed within the DATA4CIRC EU project before being
open-sourced; changes prior to the initial open-source release are not
listed here.
