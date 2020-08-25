.PHONY: test
test:
	FOO=hello NUM=3 clj -A:test

.PHONY: deploy
deploy:
	clj -A:uberdeps
	env CLOJARS_USERNAME=$(CLOJARS_USERNAME) CLOJARS_PASSWORD=$(CLOJARS_PASSWORD) clj -A:deploy