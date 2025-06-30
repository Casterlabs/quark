export declare type SessionId = string;
export declare type EgressId = string;
export declare type ForeignId = string | null;

export declare interface Session {
	id: SessionId;
	createdAt: number;
	info: {
		video: {
			id: number;
			bitrate: number;
			codec: string;
			keyFrameInterval: number;
			frameRate: number;
			pixelFormat: string;
			aspectRatio: string;
			width: number;
			height: number;
		}[];
		audio: {
			id: number;
			bitrate: number;
			codec: string;
			layout: string;
			channels: number;
			sampleRate: number;
		}[];
	};
}

export declare interface SessionEgress {
	id: EgressId;
	createdAt: number;
	type: 'HTTP_PLAYBACK' | 'RTMP';
	fid?: ForeignId;
}

export default class QuarkInstance {
	public id: string;
	public _address: string;
	private _token: string;

	constructor(id: string, address: string, token: string) {
		this.id = id;
		this._address = address;
		this._token = token;
	}

	private async apiCall(route: string, init: RequestInit = {}) {
		init.headers = { ...(init.headers || {}), Authorization: `Bearer ${this._token}` };

		const { data, error } = await (await fetch(this._address + route, init)).json();
		if (error) throw error;
		return data;
	}

	async healthCheck(): Promise<boolean> {
		try {
			return (
				await fetch(this._address + '/_healthcheck', {
					headers: {
						Authorization: `Bearer ${this._token}`
					}
				})
			).ok;
		} catch (e) {
			return false;
		}
	}

	async listSessions(): Promise<SessionId[]> {
		return await this.apiCall('/sessions');
	}

	async sessionInfo(sid: SessionId): Promise<Session> {
		return await this.apiCall(`/session/${encodeURI(sid)}`);
	}

	async endSession(sid: SessionId): Promise<void> {
		await this.apiCall(`/session/${encodeURI(sid)}`, {
			method: 'DELETE'
		});
	}

	async listSessionEgress(sid: SessionId): Promise<SessionEgress[]> {
		return await this.apiCall(`/session/${encodeURI(sid)}/egress`);
	}

	async endSessionEgress(sid: SessionId, eid: EgressId): Promise<void> {
		await this.apiCall(`/session/${encodeURI(sid)}/egress/${encodeURI(eid)}`, {
			method: 'DELETE'
		});
	}

	async startSessionEgress(sid: SessionId, format: 'RTMP', url: string, fid: ForeignId): Promise<void> {
		await this.apiCall(`/session/${encodeURI(sid)}/egress/external/${encodeURI(format.toLowerCase())}`, {
			method: 'POST',
			body: JSON.stringify({
				foreignId: fid,
				url: url
			})
		});
	}

	sessionPlaybackUrl(sid: SessionId, format: 'FLV' | 'TS' | 'MKV' | 'WEBM' | 'OPUS' | 'MP3'): string {
		// We include the ?v=date to ensure that the browser doesn't cache the file.
		return `${this._address}/session/${encodeURI(sid)}/egress/playback/${encodeURI(format.toLowerCase())}?v=${Date.now()}&authorization=${this._token}`;
	}

	sessionThumbnailUrl(sid: SessionId): string {
		return `${this._address}/session/${encodeURI(sid)}/egress/thumbnail?authorization=${this._token}`;
	}

	async startIngress(id: SessionId, source: string, loop: boolean) {
		await this.apiCall(`/session/ingress`, {
			method: 'POST',
			body: JSON.stringify({
				id: id,
				source: source,
				loop: loop
			})
		});
	}
}
