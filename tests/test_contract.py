"""Independent behavioral checks for the public agreement API contract."""

import base64
import tempfile
import unittest
import uuid
from datetime import datetime, timedelta, timezone
from pathlib import Path

from server.service import Service


class ContractTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="pact-contract-")
        self.addCleanup(self.temp.cleanup)
        self.now = datetime(2026, 9, 26, 8, tzinfo=timezone.utc)
        self.service = Service(Path(self.temp.name) / "contract.sqlite3", clock=lambda: self.now)
        self.addCleanup(self.service.close)

    def call(self, method, path, actor=None, body=None, status=200):
        response = self.service.handle(method, path, body, actor["token"] if actor else None)
        self.assertEqual(status, response.status, f"{method} {path}: {response.body}")
        return response.body

    def write(self, path, actor, status=200, **body):
        return self.call("POST", path, actor, {"requestId": str(uuid.uuid4()), **body}, status)

    def account(self, handle):
        return self.call("POST", "/v1/register", body={
            "handle": handle, "password": "synthetic passphrase 2026", "displayName": handle,
        })

    def pair(self):
        issuer, executor = self.account("issuer"), self.account("executor")
        relationship = self.write("/v1/relationships", issuer, name="我们的约定", type="couple", mode="shared")
        relationship = self.write("/v1/relationships/join", executor, inviteCode=relationship["inviteCode"])
        return issuer, executor, relationship

    def agreement(self, issuer, executor, relationship, **overrides):
        return self.write("/v1/agreements", issuer, **{
            "relationshipId": relationship["id"], "executorId": executor["user"]["id"],
            "title": "一次约定", "conditions": "共同完成一次散步", "reward": "周末一起看电影",
            "dueAt": "2026-09-27T08:00:00Z", "targetCount": 1,
            "redeemWithinDays": 7, "publish": True, **overrides,
        })

    def action(self, item, actor, action, status=200, **fields):
        return self.write(f"/v1/agreements/{item['id']}/actions", actor, status,
                          expectedVersion=item["version"], action=action, **fields)

    def state(self, actor):
        return self.call("GET", "/v1/state", actor)

    def current(self, actor, item):
        return next(value for value in self.state(actor)["agreements"] if value["id"] == item["id"])

    def achieve(self, issuer, executor, relationship):
        item = self.agreement(issuer, executor, relationship)
        item = self.action(item, executor, "accept")
        item = self.action(item, executor, "submit", note="约定已经完成")
        return self.action(item, issuer, "review", approved=True)

    def test_outsider_cannot_read_or_write_and_shared_issuer_cannot_accept(self):
        issuer, executor, relationship = self.pair()
        outsider = self.account("outsider")
        item = self.agreement(issuer, executor, relationship)
        private = self.state(outsider)
        self.assertEqual([], private["relationships"])
        self.assertEqual([], private["agreements"])
        self.action(item, outsider, "accept", status=404)
        self.action(item, issuer, "accept", status=403)
        self.assertEqual(item, self.current(issuer, item), "Rejected writes changed the agreement")
        item = self.action(item, executor, "accept")
        self.assertEqual("active", item["taskState"])

    def test_shared_reward_cannot_be_received_by_its_giver(self):
        issuer, executor, relationship = self.pair()
        item = self.achieve(issuer, executor, relationship)
        item = self.action(item, issuer, "give", note="已经给出")
        self.action(item, issuer, "receive", status=403)
        self.assertEqual("offered", self.current(executor, item)["rewardState"])
        item = self.action(item, executor, "dispute", note="目前还没有收到")
        self.assertEqual(("achieved", "pending"), (item["taskState"], item["rewardState"]))
        item = self.action(item, issuer, "give", note="重新约定交付后已给出")
        item = self.action(item, executor, "receive")
        self.assertEqual("received", item["rewardState"])
        self.assertEqual("independent", item["confirmationMode"])
        self.assertTrue(all(event["proxy"] is False for event in item["events"]))

    def test_stale_version_fails_without_changing_progress_or_events(self):
        issuer, executor, relationship = self.pair()
        original = self.agreement(issuer, executor, relationship, targetCount=2)
        current = self.action(original, executor, "accept")
        self.action(original, executor, "progress", status=409, amount=1, note="过时页面")
        self.assertEqual(current, self.current(executor, original))
        progressed = self.action(current, executor, "progress", amount=1, note="刷新后记录")
        self.assertEqual(1, progressed["progressCount"])
        self.assertEqual(current["version"] + 1, progressed["version"])

    def test_identical_retry_replays_but_request_id_cannot_change_action_or_payload(self):
        issuer, executor, relationship = self.pair()
        item = self.agreement(issuer, executor, relationship, targetCount=2)
        item = self.action(item, executor, "accept")
        path = f"/v1/agreements/{item['id']}/actions"
        body = {"requestId": str(uuid.uuid4()), "expectedVersion": item["version"],
                "action": "progress", "amount": 1, "note": "第一次散步"}
        first = self.call("POST", path, executor, body)
        self.assertEqual(first, self.call("POST", path, executor, body))
        self.call("POST", path, executor, {**body, "note": "修改了内容"}, status=409)
        self.call("POST", path, executor, {**body, "action": "comment"}, status=409)
        persisted = self.current(executor, item)
        self.assertEqual(first, persisted)
        self.assertEqual(1, persisted["progressCount"])
        self.assertEqual(1, sum(event["action"] == "progress" for event in persisted["events"]))

    def test_timely_submission_survives_late_review_and_reward_deadline_starts_on_review(self):
        issuer, executor, relationship = self.pair()
        item = self.agreement(issuer, executor, relationship, dueAt="2026-09-26T09:00:00Z")
        item = self.action(item, executor, "accept")
        self.now = datetime(2026, 9, 26, 8, 59, tzinfo=timezone.utc)
        item = self.action(item, executor, "submit", note="按时提交")
        submitted_at = item["submission"]["at"]
        self.now = datetime(2026, 9, 28, 10, tzinfo=timezone.utc)
        waiting = self.current(issuer, item)
        self.assertEqual("submitted", waiting["taskState"])
        self.assertEqual(submitted_at, waiting["submission"]["at"])
        item = self.action(waiting, issuer, "review", approved=True)
        self.assertEqual(("achieved", "pending"), (item["taskState"], item["rewardState"]))
        self.assertEqual("2026-09-28T10:00:00Z", item["achievedAt"])
        self.assertEqual("2026-10-05T10:00:00Z", item["rewardDueAt"])

    def test_pending_cancellation_cannot_erase_reward_achieved_while_waiting(self):
        issuer, executor, relationship = self.pair()
        item = self.agreement(issuer, executor, relationship)
        item = self.action(item, executor, "accept")
        item = self.action(item, issuer, "propose_cancel", note="提出取消但尚未达成一致")
        self.assertEqual("cancel", item["proposal"]["kind"])
        item = self.action(item, executor, "submit", note="仍按原约定完成")
        item = self.action(item, issuer, "review", approved=True)
        self.action(item, executor, "resolve_proposal", status=409, approved=True)
        after = self.current(executor, item)
        self.assertEqual(item, after)
        self.assertEqual(("achieved", "pending"), (after["taskState"], after["rewardState"]))

    def test_ended_relationship_preserves_pending_reward_and_allows_settlement_only(self):
        issuer, executor, relationship = self.pair()
        item = self.achieve(issuer, executor, relationship)
        ended = self.write(f"/v1/relationships/{relationship['id']}/actions", executor,
                           expectedVersion=relationship["version"], action="end")
        self.assertEqual("ended", ended["status"])
        self.assertEqual(item, self.current(executor, item))
        self.action(item, issuer, "comment", status=409, note="解除后普通互动应停止")
        self.action(item, issuer, "propose_cancel", status=409, note="不能取消已达成奖励")
        item = self.action(item, issuer, "give", note="兑现解除前已达成的奖励")
        item = self.action(item, executor, "receive")
        self.assertEqual("received", self.current(issuer, item)["rewardState"])
        self.assertEqual("ended", self.state(issuer)["relationships"][0]["status"])

    def test_proxy_completion_keeps_child_identity_and_parent_audit_source(self):
        parent = self.account("parent")
        relationship = self.write("/v1/relationships", parent, name="亲子约定",
                                  type="parent_child", mode="proxy", childName="小芽")
        child = next(member for member in relationship["members"] if member["kind"] == "child")
        item = self.agreement(parent, {"user": child}, relationship)
        item = self.action(item, parent, "accept")
        item = self.action(item, parent, "submit", note="家长记录孩子已完成")
        item = self.action(item, parent, "review", approved=True)
        item = self.action(item, parent, "give", note="家长记录奖励已给出")
        item = self.action(item, parent, "receive")
        self.assertEqual(child["id"], item["executorId"])
        self.assertNotEqual(parent["user"]["id"], item["executorId"])
        self.assertEqual("proxy", item["confirmationMode"])
        self.assertEqual("received", item["rewardState"])
        delegated = [event for event in item["events"] if event["action"] in {"accept", "submit", "receive"}]
        self.assertEqual(3, len(delegated))
        for event in delegated:
            self.assertIs(event["proxy"], True)
            self.assertEqual(parent["user"]["id"], event["actorId"])
        self.assertIs(item["submission"]["proxy"], True)
        self.assertEqual(parent["user"]["id"], item["submission"]["actorId"])

    def test_media_becomes_visible_only_to_its_agreement_and_cannot_be_relinked_by_viewer(self):
        issuer, executor, relationship = self.pair()
        outsider = self.account("outsider")
        image = base64.b64decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aB1UAAAAASUVORK5CYII=")
        media = self.write("/v1/media", executor, mime="image/png", data=base64.b64encode(image).decode())
        media_path = f"/v1/media/{media['id']}"
        self.call("GET", media_path, issuer, status=404)
        self.call("GET", media_path, outsider, status=404)
        item = self.agreement(issuer, executor, relationship)
        item = self.action(item, executor, "accept")
        item = self.action(item, executor, "submit", note="完成照片", mediaIds=[media["id"]])
        visible = self.service.handle("GET", media_path, token=issuer["token"])
        self.assertEqual(200, visible.status)
        self.assertEqual(image, visible.body)
        self.assertEqual("image/png", visible.headers["Content-Type"])
        self.assertEqual("no-store", visible.headers["Cache-Control"])
        self.call("GET", media_path, outsider, status=404)

        other = self.write("/v1/relationships", outsider, name="另一段关系", type="custom", mode="shared")
        other = self.write("/v1/relationships/join", issuer, inviteCode=other["inviteCode"])
        unrelated = self.agreement(outsider, issuer, other)
        unrelated = self.action(unrelated, issuer, "accept")
        own_media = self.write("/v1/media", issuer, mime="image/png", data=base64.b64encode(image).decode())
        rejected = self.service.handle("POST", f"/v1/agreements/{unrelated['id']}/actions", {
            "requestId": str(uuid.uuid4()), "expectedVersion": unrelated["version"],
            "action": "submit", "note": "尝试将别人的照片转给另一个关系",
            "mediaIds": [own_media["id"], media["id"]],
        }, issuer["token"])
        # The contract requires denial; it does not prescribe 403 versus privacy-preserving 404 here.
        self.assertIn(rejected.status, (403, 404), rejected.body)
        self.assertEqual(unrelated, self.current(issuer, unrelated))
        self.call("GET", media_path, outsider, status=404)
        self.call("GET", f"/v1/media/{own_media['id']}", outsider, status=404)

    def test_shared_member_cannot_approve_own_amendment_and_old_terms_remain_until_peer_confirms(self):
        issuer, executor, relationship = self.pair()
        item = self.agreement(issuer, executor, relationship)
        item = self.action(item, executor, "accept")
        before_reward = item["reward"]
        item = self.action(item, issuer, "propose_amend", changes={"reward": "周末去科技馆"}, note="提出新奖励")
        self.assertEqual(before_reward, item["reward"])
        self.action(item, issuer, "resolve_proposal", status=403, approved=True)
        self.assertEqual(item, self.current(executor, item))
        item = self.action(item, executor, "resolve_proposal", approved=True)
        self.assertEqual("周末去科技馆", item["reward"])
        self.assertIsNone(item["proposal"])
        event_text = str(item["events"])
        self.assertIn(before_reward, event_text)
        self.assertIn("周末去科技馆", event_text)


if __name__ == "__main__":
    unittest.main()
