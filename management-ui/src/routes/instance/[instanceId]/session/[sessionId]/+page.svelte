<script lang="ts">
	import { goto } from '$app/navigation';

	import LoadingSpinner from '$lib/layout/LoadingSpinner.svelte';
	import Modal from '$lib/layout/Modal.svelte';
	import StatsForNerds from '$lib/layout/StatsForNerds.svelte';
	import Player from '$lib/layout/player/Player.svelte';
	import { IconTrash } from '@casterlabs/heroicons-svelte';
	import { Button, Input, Select } from '@casterlabs/ui';

	import type { PageProps } from './$types';

	let { data }: PageProps = $props();

	let showEgressManagementModal = $state(false);
	let egressManagementModalRerender = $state(0);

	let showStartEgressModal = $state(false);
	let startEgressProtocol: 'RTMP' | 'PIPELINE' = $state('RTMP');

	let startEgressRTMPUrl = $state('');
	let startEgressPIPELINEResultId = $state('');
	let startEgressPIPELINECommand = $state('');
</script>

{#await data.session}
	<div class="h-full flex items-center justify-center">
		<span class="text-3xl">
			<LoadingSpinner />
		</span>
	</div>
{:then session}
	{#if session}
		<div class="space-y-4 max-w-4xl mx-auto">
			<Player instance={data.instance} {session} />

			<ul class="flex items-row h-16 space-x-2">
				<li class="basis-1/3">
					<Button
						class="block w-full h-full"
						onclick={async () => {
							await data.instance.endSession(session.id);
							goto(`/instance/${encodeURI(data.instance.id)}`);
						}}
					>
						End Session
					</Button>
				</li>
				<li class="basis-1/3">
					<Button class="block w-full h-full" onclick={() => (showEgressManagementModal = true)}>Egress Management</Button>
				</li>
				<li class="basis-1/3">
					<Button class="block w-full h-full" onclick={() => (showStartEgressModal = true)}>Start Egress</Button>
				</li>
			</ul>

			<StatsForNerds {session} />
		</div>

		<Modal bind:showModal={showEgressManagementModal}>
			<h1 class="font-semibold text-lg">Active Egresses</h1>
			{#key egressManagementModalRerender}
				{#await data.instance.listSessionEgress(session.id)}
					<div class="flex items-center justify-center">
						<span class="text-3xl">
							<LoadingSpinner />
						</span>
					</div>
				{:then egresses}
					{#if egresses.length == 0}
						<div class="text-center">
							<span class="text-base-11 text-sm">No active egresses</span>
						</div>
					{:else}
						<ul class="space-y-1 max-h-96 overflow-y-auto">
							{#each egresses as egress}
								<li class="flex flex-row bg-base-3 rounded-sm border-base-7 border hover:border-base-8 p-2">
									<div class="flex-1 truncate text-left">
										<h2>Type: <span class="font-semibold">{egress.type}</span></h2>
										<h3 class="text-sm">
											Foreign ID: <pre class="inline">{egress.fid}</pre>
										</h3>
									</div>
									<button
										class="cursor-pointer p-2"
										onclick={async () => {
											await data.instance.endSessionEgress(session.id, egress.id);
											egressManagementModalRerender++;
										}}
									>
										<span class="sr-only">Delete</span>
										<IconTrash theme="micro" />
									</button>
								</li>
							{/each}
						</ul>
					{/if}
				{/await}
			{/key}
		</Modal>

		<Modal bind:showModal={showStartEgressModal}>
			<h1 class="font-semibold text-lg">Start Egress</h1>
			<div class="space-y-1 flex">
				<Select
					class="px-1 py-0.5 h-6"
					style="border-top-right-radius: 0 !important; border-bottom-right-radius: 0 !important; border-right-width: 0px;"
					bind:value={startEgressProtocol}
				>
					<option>RTMP</option>
					<option>PIPELINE</option>
				</Select>
				{#if startEgressProtocol == 'RTMP'}
					<Input
						type="password"
						placeholder="URL"
						bind:value={startEgressRTMPUrl}
						class="flex-1 px-1 py-0.5 h-6"
						style="border-top-left-radius: 0 !important; border-bottom-left-radius: 0 !important;"
					/>
				{:else if startEgressProtocol == 'PIPELINE'}
					<Input
						type="input"
						placeholder="Result ID - Blank for none"
						bind:value={startEgressPIPELINEResultId}
						class="flex-1 px-1 py-0.5 h-6"
						style="border-top-left-radius: 0 !important; border-bottom-left-radius: 0 !important;"
					/>
				{/if}
			</div>

			{#if startEgressProtocol == 'PIPELINE'}
				<textarea placeholder="Command - Each argument on a new line" bind:value={startEgressPIPELINECommand} class="flex-1 px-1 py-0.5 w-full h-24"></textarea>
			{/if}

			<Button
				class="px-1 w-full"
				onclick={() => {
					switch (startEgressProtocol) {
						case 'RTMP': {
							const fid = `${new URL(startEgressRTMPUrl).hostname}-${Math.random().toString(28).substring(2)}`;
							data.instance.startSessionEgressRTMP(session.id, startEgressRTMPUrl, fid);
							break;
						}

						case 'PIPELINE': {
							const command = startEgressPIPELINECommand.split('\n');
							const fid = `pipeline-${command[0]}-${Math.random().toString(28).substring(2)}`;
							data.instance.startSessionEgressPipeline(session.id, fid, startEgressPIPELINEResultId.length == 0 ? null : startEgressPIPELINEResultId, command);
							break;
						}
					}

					showStartEgressModal = false;
				}}
			>
				Add
			</Button>
		</Modal>
	{/if}
{/await}

<style>
	textarea {
		border-radius: var(--clui-radius, 0);
		border-width: 0.0625rem;
		border-style: solid;
		border-color: transparent;
		background-color: transparent;
		color: currentColor;
		font-size: 0.8rem;
	}

	textarea:not(.borderless) {
		border-color: var(--clui-color-base-7);
		background-color: var(--clui-color-base-3);
		color: var(--clui-color-base-12);
	}

	textarea:not(.borderless)::placeholder {
		color: var(--clui-color-base-11);
	}
</style>
