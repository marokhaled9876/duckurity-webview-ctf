# Flag-catcher service for the challenge's "attacker infrastructure".
# This is what OpenLabs spins up so a tester's exploit has something to
# fetch() the flag to, without needing to run anything locally.
FROM python:3.12-alpine

WORKDIR /app

# Only the listener is needed inside the container; payload.html is
# served separately by the challenge-files service (see docker-compose.yml)
# so a tester can still read/host it themselves if they prefer.
COPY solve/listener.py .

EXPOSE 8000

# -u: unbuffered stdout so `docker logs -f` shows the flag immediately
CMD ["python3", "-u", "listener.py"]
