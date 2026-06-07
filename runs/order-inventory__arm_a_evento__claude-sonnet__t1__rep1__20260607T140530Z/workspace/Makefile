# Workspace verbs. `make test` is the definition of done:
# build -> (re)start the app -> wait healthy -> run the acceptance suite.
.PHONY: build run stop test

build:
	mvn -q -B -DskipTests package

run: build
	./scripts/app.sh restart

stop:
	./scripts/app.sh stop

test: run
	BASE_URL=http://localhost:$${PORT:-8080} TASK=$${TASK:-t1} \
	uv run --no-project --with pytest --with httpx pytest acceptance-tests -q
