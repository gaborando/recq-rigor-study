# Micro reactor verbs. `make test` is the definition of done.
.PHONY: build run stop test
build:
	mvn -q -B -DskipTests package
run: build
	./scripts/up.sh
stop:
	./scripts/down.sh
test: run
	BASE_URL=http://localhost:$${EDGE_PORT:-8080} TASK=$${TASK:-t1} TOPOLOGY=micro \
	uv run --no-project --with pytest --with httpx pytest acceptance-tests -q
