<script lang="ts">
	import type { SessionId } from '$lib/quark';

	import Card from '$lib/layout/Card.svelte';
	import LoadingSpinner from '$lib/layout/LoadingSpinner.svelte';
	import Modal from '$lib/layout/Modal.svelte';
	import { Button, Input } from '@casterlabs/ui';

	import type { PageProps } from './$types';

	let { data }: PageProps = $props();

	let validThumbnails: SessionId[] = $state([]);

	let rerender = $state(0);

	let showIngressStartModal = $state(false);
	let ingressStartId = $state('');
	let ingressStartSource = $state('');
	let ingressStartLoop = $state(false);
</script>

<div class="h-full flex items-center justify-center">
	{#key rerender}
		{#await data.instance.listSessions()}
			<span class="text-3xl">
				<LoadingSpinner />
			</span>
		{:then ids}
			<ul class="max-w-2xl justify-center flex flex-wrap">
				{#each ids as sid}
					<li>
						<Card href="/instance/{encodeURI(data.instance.id)}/session/{encodeURI(sid)}" class="overflow-hidden relative">
							<img
								class="absolute inset-0 w-full h-full object-cover border-0 border-transparent"
								class:hidden={!validThumbnails.includes(sid)}
								src={data.instance.sessionThumbnailUrl(sid)}
								alt=""
								onload={() => validThumbnails.push(sid)}
							/>
							<div
								class="absolute inset-0 flex items-center justify-center z-20"
								class:bg-[#000000aa]={validThumbnails.includes(sid)}
								class:hover:bg-[#00000066]={validThumbnails.includes(sid)}
							>
								{sid}
							</div>
						</Card>
					</li>
				{/each}

				<li>
					<Card onclick={() => (showIngressStartModal = true)}>
						<div class=" w-full h-full flex items-center justify-center">
							<span class="sr-only"> Start Ingress </span>
							<span aria-hidden="true"> + </span>
						</div>
					</Card>
				</li>
			</ul>
		{/await}
	{/key}
</div>

<Modal bind:showModal={showIngressStartModal}>
	<h1 class="font-semibold text-lg">Start Egress</h1>
	<div class="space-y-1">
		<Input type="text" placeholder="ID" bind:value={ingressStartId} class="w-full px-1 py-0.5" />
		<Input type="text" placeholder="Source" bind:value={ingressStartSource} class="w-full px-1 py-0.5" />
		<label class="text-sm">
			Loop? <Input class="align-sub" type="checkbox" placeholder="Token" bind:checked={ingressStartLoop} />
		</label>
	</div>
	<Button
		class="px-1 w-full"
		onclick={async () => {
			await data.instance.startIngress(ingressStartId, ingressStartSource, ingressStartLoop);
			rerender++;
			showIngressStartModal = false;
		}}
	>
		Start
	</Button>
</Modal>
