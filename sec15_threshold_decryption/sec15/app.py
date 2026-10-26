"""Small Flask API that exposes SEC-15 over HTTP.

POST /ballots       {"choice": "..."}           cast a ballot (poll must be open)
POST /poll/close                                close the poll
POST /tally/decrypt {"shares": ["evs1-...", ...]}
    -> 423 Locked   if the poll is still open
    -> 403          if fewer than 3 valid trustee shares are given
    -> 200          with the decrypted ballots otherwise
"""

from __future__ import annotations

from flask import Flask, jsonify, request

from .election import Election, PollStillOpen
from .election_keys import key_ceremony
from .shamir import ShareError


def create_app(election: Election | None = None, shares: list[str] | None = None) -> Flask:
    app = Flask(__name__)

    if election is None:
        keys, shares = key_ceremony()
        election = Election(keys)
    app.config["ELECTION"] = election
    app.config["DEMO_SHARES"] = shares  # demo only: real trustees keep their own share

    @app.post("/ballots")
    def cast():
        try:
            election.cast(request.get_json(force=True))
        except ValueError as error:
            return jsonify(error=str(error)), 409
        return jsonify(cast=len(election.ballot_box)), 201

    @app.post("/poll/close")
    def close():
        election.close_poll()
        return jsonify(status=election.status)

    @app.post("/tally/decrypt")
    def decrypt():
        body = request.get_json(silent=True) or {}
        try:
            ballots = election.decrypt_all(body.get("shares", []))
        except PollStillOpen as error:
            return jsonify(error=str(error)), 423  # 423 Locked
        except ShareError as error:
            return jsonify(error=str(error)), 403
        return jsonify(ballots=ballots, count=len(ballots))

    return app


if __name__ == "__main__":
    create_app().run(host="127.0.0.1", port=5015)