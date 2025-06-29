# [2.0.0](https://github.com/gravitee-io/gravitee-notifier-email/compare/1.5.2...2.0.0) (2025-06-20)


### Bug Fixes

* upgrade gravitee dependencies ([acaddf5](https://github.com/gravitee-io/gravitee-notifier-email/commit/acaddf5e58abce2fcfd0efe7770041b5b09f8757))


### BREAKING CHANGES

* this plugin now requires java 21

## [1.5.2](https://github.com/gravitee-io/gravitee-notifier-email/compare/1.5.1...1.5.2) (2024-02-21)


### Bug Fixes

* update log trace to remove typo and make information available ([bf7066c](https://github.com/gravitee-io/gravitee-notifier-email/commit/bf7066c9e00cc938332b2c336bf1969b042273f6))

## [1.5.1](https://github.com/gravitee-io/gravitee-notifier-email/compare/1.5.0...1.5.1) (2023-03-16)


### Bug Fixes

* bump all dependencies ([9c82627](https://github.com/gravitee-io/gravitee-notifier-email/commit/9c82627a6e9d765d7abbc4ba37a3c15fb8ded28a))
* convert new line `\n` to `<br>`` tag to have it displayed in html email ([439b66c](https://github.com/gravitee-io/gravitee-notifier-email/commit/439b66ce2e22717dde6569ed484d5a2c8b91a637))

# [1.5.0](https://github.com/gravitee-io/gravitee-notifier-email/compare/1.4.2...1.5.0) (2022-11-24)


### Features

* handle auth methods in mail configuration ([e807232](https://github.com/gravitee-io/gravitee-notifier-email/commit/e8072324adf0b9dbcd0c63ef13ebdc41923317aa))

## [1.4.2](https://github.com/gravitee-io/gravitee-notifier-email/compare/1.4.1...1.4.2) (2022-07-21)


### Bug Fixes

* do not include attachment if the resource path isn't on the right folder ([9581009](https://github.com/gravitee-io/gravitee-notifier-email/commit/9581009355be1741cd955a2f9ff78028f7fc4207)), closes [gravitee-io/issues#8091](https://github.com/gravitee-io/issues/issues/8091)

## [1.4.1](https://github.com/gravitee-io/gravitee-notifier-email/compare/1.4.0...1.4.1) (2022-02-23)


### Bug Fixes

* **config:** remove sensitive flag to the username ([28a852e](https://github.com/gravitee-io/gravitee-notifier-email/commit/28a852eeeb8ac920085e0ae8bdfb34ab32aebf73)), closes [gravitee-io/issues#7166](https://github.com/gravitee-io/issues/issues/7166)

# [1.4.0](https://github.com/gravitee-io/gravitee-notifier-email/compare/1.3.2...1.4.0) (2022-01-27)


### Features

* **mail:** support multi recipients and add unit tests ([b88b848](https://github.com/gravitee-io/gravitee-notifier-email/commit/b88b8487f9992fabc6c465f959de4db1a3174e4e)), closes [gravitee-io/issues#6992](https://github.com/gravitee-io/issues/issues/6992)

## [1.3.2](https://github.com/gravitee-io/gravitee-notifier-email/compare/1.3.1...1.3.2) (2022-01-25)


### Bug Fixes

* **email:** Split the recipients once the parameter has been processed by Freemarker ([dd4126e](https://github.com/gravitee-io/gravitee-notifier-email/commit/dd4126e0327dc0cd06880d55ce5bc579301ca74e)), closes [gravitee-io/issues#6992](https://github.com/gravitee-io/issues/issues/6992)

## [1.3.1](https://github.com/gravitee-io/gravitee-notifier-email/compare/1.3.0...1.3.1) (2022-01-24)


### Bug Fixes

* **plugins:** do not expose sensitive configuration ([47e5eb6](https://github.com/gravitee-io/gravitee-notifier-email/commit/47e5eb6606d6dfaa5bcede12c638d81d8615602a))
