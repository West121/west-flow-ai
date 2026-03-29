#!/usr/bin/env python3
from __future__ import annotations

import argparse
import concurrent.futures
import statistics
import time
from dataclasses import dataclass

import requests


DEFAULT_BASE_URL = "http://127.0.0.1:8080/api/v1"


@dataclass
class Sample:
    name: str
    status_code: int
    elapsed_ms: float


def login(base_url: str, username: str, password: str) -> str:
    response = requests.post(
        f"{base_url}/auth/login",
        json={"username": username, "password": password},
        timeout=10,
    )
    response.raise_for_status()
    return response.json()["data"]["accessToken"]


def percentile(values: list[float], p: float) -> float:
    if not values:
        return 0.0
    ordered = sorted(values)
    index = max(0, min(len(ordered) - 1, round((len(ordered) - 1) * p)))
    return ordered[index]


def print_summary(title: str, samples: list[Sample]) -> None:
    grouped: dict[str, list[Sample]] = {}
    for sample in samples:
      grouped.setdefault(sample.name, []).append(sample)

    print(f"\n== {title} ==")
    for name, bucket in grouped.items():
        durations = [item.elapsed_ms for item in bucket]
        success = sum(1 for item in bucket if 200 <= item.status_code < 300)
        print(
            f"{name}: count={len(bucket)} success={success}/{len(bucket)} "
            f"avg={statistics.mean(durations):.1f}ms "
            f"p95={percentile(durations, 0.95):.1f}ms "
            f"max={max(durations):.1f}ms"
        )


def read_flow(base_url: str, token: str) -> list[Sample]:
    headers = {"Authorization": f"Bearer {token}"}
    samples: list[Sample] = []

    def timed_post(name: str, path: str, payload: dict) -> requests.Response:
        start = time.perf_counter()
        response = requests.post(
            f"{base_url}{path}",
            headers=headers,
            json=payload,
            timeout=20,
        )
        samples.append(
            Sample(name=name, status_code=response.status_code, elapsed_ms=(time.perf_counter() - start) * 1000)
        )
        return response

    tasks = timed_post(
        "tasks.page",
        "/process-runtime/tasks/page",
        {"page": 1, "pageSize": 20, "keyword": "", "filters": [], "sorts": [], "groups": []},
    )
    timed_post(
        "approval-sheets.page",
        "/process-runtime/approval-sheets/page",
        {
            "page": 1,
            "pageSize": 20,
            "keyword": "",
            "filters": [],
            "sorts": [],
            "groups": [],
            "view": "TODO",
            "businessTypes": [],
        },
    )
    records = tasks.json().get("data", {}).get("records", [])
    if records:
        task_id = records[0]["taskId"]
        start = time.perf_counter()
        detail = requests.get(
            f"{base_url}/process-runtime/tasks/{task_id}",
            headers=headers,
            timeout=20,
        )
        samples.append(
            Sample(
                name="tasks.detail",
                status_code=detail.status_code,
                elapsed_ms=(time.perf_counter() - start) * 1000,
            )
        )

    return samples


def action_flow(base_url: str, admin_token: str, approver_token: str, iteration: int) -> list[Sample]:
    admin_headers = {"Authorization": f"Bearer {admin_token}"}
    approver_headers = {"Authorization": f"Bearer {approver_token}"}
    samples: list[Sample] = []

    def timed(name: str, fn):
        start = time.perf_counter()
        response = fn()
        samples.append(
            Sample(name=name, status_code=response.status_code, elapsed_ms=(time.perf_counter() - start) * 1000)
        )
        return response

    created = timed(
        "oa.leaves.create",
        lambda: requests.post(
            f"{base_url}/oa/leaves",
            headers=admin_headers,
            json={
                "leaveType": "PERSONAL",
                "days": 1,
                "urgent": False,
                "reason": f"perf-{int(time.time() * 1000)}-{iteration}",
                "managerUserId": "usr_002",
            },
            timeout=20,
        ),
    )
    data = created.json().get("data", {})
    task_id = data.get("firstActiveTask", {}).get("taskId")
    if not task_id:
        return samples

    timed(
        "tasks.claim",
        lambda: requests.post(
            f"{base_url}/process-runtime/tasks/{task_id}/claim",
            headers=approver_headers,
            json={"comment": "perf claim"},
            timeout=20,
        ),
    )
    timed(
        "tasks.complete",
        lambda: requests.post(
            f"{base_url}/process-runtime/tasks/{task_id}/complete",
            headers=approver_headers,
            json={"action": "APPROVE", "comment": "perf approve", "taskFormData": {}},
            timeout=20,
        ),
    )
    return samples


def run_read_test(base_url: str, username: str, password: str, concurrency: int, iterations: int) -> list[Sample]:
    token = login(base_url, username, password)
    samples: list[Sample] = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=concurrency) as executor:
        futures = [executor.submit(read_flow, base_url, token) for _ in range(iterations)]
        for future in concurrent.futures.as_completed(futures):
            samples.extend(future.result())
    return samples


def run_action_test(base_url: str, concurrency: int, iterations: int) -> list[Sample]:
    admin_token = login(base_url, "admin", "admin123")
    approver_token = login(base_url, "zhangsan", "123456")
    samples: list[Sample] = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=concurrency) as executor:
        futures = [
            executor.submit(action_flow, base_url, admin_token, approver_token, index)
            for index in range(iterations)
        ]
        for future in concurrent.futures.as_completed(futures):
            samples.extend(future.result())
    return samples


def main() -> None:
    parser = argparse.ArgumentParser(description="Approval runtime baseline performance test")
    parser.add_argument("--base-url", default=DEFAULT_BASE_URL)
    parser.add_argument("--username", default="zhangsan")
    parser.add_argument("--password", default="123456")
    parser.add_argument("--read-concurrency", type=int, default=10)
    parser.add_argument("--read-iterations", type=int, default=30)
    parser.add_argument("--action-concurrency", type=int, default=3)
    parser.add_argument("--action-iterations", type=int, default=12)
    args = parser.parse_args()

    read_samples = run_read_test(
        args.base_url,
        args.username,
        args.password,
        args.read_concurrency,
        args.read_iterations,
    )
    print_summary("READ BASELINE", read_samples)

    action_samples = run_action_test(
        args.base_url,
        args.action_concurrency,
        args.action_iterations,
    )
    print_summary("ACTION BASELINE", action_samples)


if __name__ == "__main__":
    main()
