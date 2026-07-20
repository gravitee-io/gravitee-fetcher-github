## [3.0.1](https://github.com/gravitee-io/gravitee-fetcher-github/compare/3.0.0...3.0.1) (2026-07-20)


### Bug Fixes

* normalize filepath and clarify not-found error ([c9948cc](https://github.com/gravitee-io/gravitee-fetcher-github/commit/c9948cc703a531e4809810dfc43bbb676b4d8e68))

# [3.0.0](https://github.com/gravitee-io/gravitee-fetcher-github/compare/2.2.1...3.0.0) (2026-07-15)


### Bug Fixes

* fail fetch when the connection stalls while reading the response ([c56722c](https://github.com/gravitee-io/gravitee-fetcher-github/commit/c56722c3a8454a4d55ef41c3e566b410664c7b76))
* log fetch errors once and unwrap the async exception wrapper ([9e2274d](https://github.com/gravitee-io/gravitee-fetcher-github/commit/9e2274d22561d1e94bb6a160f9a43e4987c35300))


### Features

* upgrade to vertx 5 ([333acac](https://github.com/gravitee-io/gravitee-fetcher-github/commit/333acac9401b6861e714cd07e8b130c790922010))


### BREAKING CHANGES

* compiled against Vert.x 5 (gravitee-bom 9.x), requires an APIM runtime on Vert.x 5

## [2.2.1](https://github.com/gravitee-io/gravitee-fetcher-github/compare/2.2.0...2.2.1) (2024-09-13)


### Bug Fixes

* do not log ResourceNotFoundException ([1f0d135](https://github.com/gravitee-io/gravitee-fetcher-github/commit/1f0d135e23eb17dd9b9f874f566ba08a8f2f6cc1))

# [2.2.0](https://github.com/gravitee-io/gravitee-fetcher-github/compare/2.1.1...2.2.0) (2024-09-13)


### Features

* add new ResourceNotFoundException ([08c20ad](https://github.com/gravitee-io/gravitee-fetcher-github/commit/08c20adbb569c145f8eeba5307ff2e9f601533c1))

## [2.1.1](https://github.com/gravitee-io/gravitee-fetcher-github/compare/2.1.0...2.1.1) (2024-09-11)


### Bug Fixes

* improve schema form ([05b705d](https://github.com/gravitee-io/gravitee-fetcher-github/commit/05b705d20369624dd6328ade8a28883b70ca86ba))

# [2.1.0](https://github.com/gravitee-io/gravitee-fetcher-github/compare/2.0.1...2.1.0) (2024-09-03)


### Features

* improve fetchCron field ([5379c88](https://github.com/gravitee-io/gravitee-fetcher-github/commit/5379c8857647c37949340a1dcc1142582bfdfe22))

## [2.0.1](https://github.com/gravitee-io/gravitee-fetcher-github/compare/2.0.0...2.0.1) (2024-06-10)


### Bug Fixes

* **deps:** update dependency org.wiremock:wiremock-standalone to v3.6.0 ([9554c5c](https://github.com/gravitee-io/gravitee-fetcher-github/commit/9554c5cca2a489f92b2768b53733a725fec27ed8))
* use right scope for lib ([174e3b2](https://github.com/gravitee-io/gravitee-fetcher-github/commit/174e3b276285cfc16c7addb81ed5a97992f1ac49))

# [2.0.0](https://github.com/gravitee-io/gravitee-fetcher-github/compare/1.6.0...2.0.0) (2024-06-05)


### chore

* bump dependencies ([bc15e1d](https://github.com/gravitee-io/gravitee-fetcher-github/commit/bc15e1d428ebef67f9e248e02421d1876dc01b9f))


### BREAKING CHANGES

* require JDK 17
