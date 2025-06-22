import QuarkInstance from './quark';

const KEY = 'quark:config';

declare interface Config {
	instances?: { id: string; address: string; token: string }[];
}

let config: Config = {};
{
	const data = localStorage.getItem(KEY);
	if (data) config = JSON.parse(data);
}

function save() {
	localStorage.setItem(KEY, JSON.stringify(config));
	location.reload();
}

let instanceCache: QuarkInstance[] | null = null;

export function instances() {
	if (instanceCache) return instanceCache;

	instanceCache = [];
	for (let i of config.instances || []) {
		instanceCache.push(new QuarkInstance(i.id, i.address, i.token));
	}
	return instanceCache;
}

export function addInstance(id: string, address: string, token: string) {
	const arr = config.instances || [];
	arr.push({ id, address, token });
	config.instances = arr;
	save();
}

export function removeInstance(id: string) {
	const arr = (config.instances || []).filter((i) => i.id !== id);
	config.instances = arr;
	save();
}
